package com.example.tethernikon.data

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PtpCameraConnector(
    private val context: Context,
    private val device: UsbDevice
) {
    private val tag = "PtpCameraConnector"
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var endpointIntr: UsbEndpoint? = null
    private var transactionId = 0
    private var isSessionOpen = false
    private val usbLock = Any()

    fun openSession(): Boolean {
        return synchronized(usbLock) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
            if (!usbManager.hasPermission(device)) {
                Log.e(tag, "No permission to open USB device: ${device.deviceName}")
                return false
            }

            connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(tag, "Failed to open UsbDeviceConnection")
                return false
            }

            // Find the Imaging interface (Class 6) or vendor-specific interface with Bulk IN/OUT endpoints
            var foundInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_VIDEO || 
                    intf.interfaceClass == 6 || 
                    intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                    foundInterface = intf
                    break
                }
            }

            // Fallback to the first interface if none matched class 6 or vendor
            if (foundInterface == null && device.interfaceCount > 0) {
                foundInterface = device.getInterface(0)
            }

            if (foundInterface == null) {
                Log.e(tag, "No suitable UsbInterface found")
                close()
                return false
            }

            usbInterface = foundInterface
            if (!connection!!.claimInterface(usbInterface, true)) {
                Log.e(tag, "Failed to claim USB interface")
                close()
                return false
            }

            // Locate bulk and interrupt endpoints
            for (j in 0 until usbInterface!!.endpointCount) {
                val ep = usbInterface!!.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        endpointIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        endpointOut = ep
                    }
                } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        endpointIntr = ep
                    }
                }
            }

            if (endpointIn == null || endpointOut == null) {
                Log.e(tag, "Bulk endpoints not found")
                close()
                return false
            }

            transactionId = 1
            isSessionOpen = false

            // Send PTP InitSession (opcode: 0x1002)
            try {
                val success = initSession()
                if (success) {
                    isSessionOpen = true
                    Log.d(tag, "PTP Session successfully opened with camera!")
                    return true
                } else {
                    Log.e(tag, "PTP InitSession returned failure")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error sending InitSession command", e)
            }

            close()
            false
        }
    }

    fun initiateCapture(): Boolean {
        return synchronized(usbLock) {
            if (connection == null || endpointOut == null || !isSessionOpen) {
                Log.e(tag, "Cannot capture: session not open")
                return false
            }

            try {
                val tx = transactionId++
                // First, try standard PTP InitiateCapture (opcode: 0x100E) with parameters (StorageID = 0, ObjectFormatCode = 0)
                val cmd = buildCommandContainer(0x100E.toShort(), tx, 0, 0)
                val sent = connection!!.bulkTransfer(endpointOut, cmd, cmd.size, 1000)
                
                var success = false
                if (sent >= 0) {
                    val response = readResponseContainer(tx)
                    if (response != null && response.responseCode == 0x2001.toShort()) { // PTP_RC_OK
                        Log.d(tag, "Physical shutter capture initiated successfully using standard PTP!")
                        success = true
                    } else {
                        val codeStr = response?.responseCode?.toInt()?.toString(16) ?: "unknown"
                        Log.d(tag, "Standard InitiateCapture returned code: 0x$codeStr. Trying Nikon vendor-specific 0x90C0...")
                    }
                } else {
                    Log.d(tag, "Standard InitiateCapture bulkTransfer failed. Trying Nikon vendor-specific 0x90C0...")
                }

                if (!success) {
                    // Try Nikon-specific Capture command (opcode: 0x90C0)
                    val tx2 = transactionId++
                    val cmd2 = buildCommandContainer(0x90C0.toShort(), tx2, 0xFFFFFFFF.toInt())
                    val sent2 = connection!!.bulkTransfer(endpointOut, cmd2, cmd2.size, 1000)
                    if (sent2 >= 0) {
                        val response2 = readResponseContainer(tx2)
                        if (response2 != null && (response2.responseCode == 0x2001.toShort() || response2.responseCode == 0x2002.toShort() || response2.responseCode == 0x2000.toShort())) {
                            Log.d(tag, "Physical shutter capture initiated successfully using Nikon-specific PTP 0x90C0!")
                            success = true
                        } else {
                            val code2Str = response2?.responseCode?.toInt()?.toString(16) ?: "unknown"
                            Log.d(tag, "Nikon 0x90C0 returned code: 0x$code2Str. Trying Nikon 0x9017...")
                            
                            // Also try 0x9017 (Nikon Capture 2) just in case
                            val tx3 = transactionId++
                            val cmd3 = buildCommandContainer(0x9017.toShort(), tx3, 0xFFFF.toInt())
                            val sent3 = connection!!.bulkTransfer(endpointOut, cmd3, cmd3.size, 1000)
                            if (sent3 >= 0) {
                                val response3 = readResponseContainer(tx3)
                                if (response3 != null && response3.responseCode == 0x2001.toShort()) {
                                    Log.d(tag, "Physical shutter capture initiated successfully using Nikon 0x9017!")
                                    success = true
                                }
                            }
                        }
                    }
                }

                success
            } catch (e: Exception) {
                Log.e(tag, "Error triggering shutter", e)
                false
            }
        }
    }

    fun getDeviceProperty(propCode: Short): Int? {
        return synchronized(usbLock) {
            if (connection == null || endpointOut == null || endpointIn == null || !isSessionOpen) {
                return null
            }

            try {
                val tx = transactionId++
                // GetDevicePropValue (opcode: 0x1015)
                val cmd = buildCommandContainer(0x1015, tx, propCode.toInt())
                val sent = connection!!.bulkTransfer(endpointOut, cmd, cmd.size, 1000)
                if (sent < 0) return null

                // Read Data Container
                val dataBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
                val readBytes = connection!!.bulkTransfer(endpointIn, dataBuffer.array(), dataBuffer.capacity(), 1000)
                if (readBytes < 12) {
                    Log.e(tag, "getDeviceProperty: read too few bytes for data container ($readBytes)")
                    return null
                }

                logPtpPacket("IN (Data-Property)", dataBuffer.array(), readBytes)

                dataBuffer.limit(readBytes)
                val length = dataBuffer.int
                val type = dataBuffer.short
                val code = dataBuffer.short
                val rTx = dataBuffer.int

                if (type != 2.toShort()) { // Not a data container
                    return null
                }

                val payloadSize = length - 12
                if (payloadSize <= 0) return null

                val value = when (payloadSize) {
                    1 -> dataBuffer.get().toInt() and 0xFF
                    2 -> dataBuffer.short.toInt() and 0xFFFF
                    4 -> dataBuffer.int
                    else -> {
                        // Larger or custom Nikon type, read first 4 bytes as standard int fallback
                        dataBuffer.int
                    }
                }

                // Consume the trailing Response container (Type 3) to clear the pipe
                readResponseContainer(tx)

                value
            } catch (e: Exception) {
                Log.e(tag, "Failed to get device property: 0x${propCode.toString(16)}", e)
                null
            }
        }
    }

    fun getCameraExposureInfo(): CameraExposureInfo {
        return synchronized(usbLock) {
            val batteryRaw = getDeviceProperty(0x5001.toShort()) // Battery Level
            val apertureRaw = getDeviceProperty(0x5007.toShort()) // Aperture / F-Number
            val shutterRaw = getDeviceProperty(0x500D.toShort()) // Shutter Speed / Exposure Time
            val isoRaw = getDeviceProperty(0x500F.toShort()) // ISO Speed

            val battery = if (batteryRaw != null) "$batteryRaw%" else "-"
            val aperture = if (apertureRaw != null && apertureRaw > 0) {
                "f/${apertureRaw / 100f}"
            } else "-"

            val shutter = if (shutterRaw != null && shutterRaw > 0) {
                // Formatter for PTP shutter speed multiplied by 10000
                when {
                    shutterRaw == 10000 -> "1s"
                    shutterRaw < 10000 -> "1/${10000 / shutterRaw}s"
                    else -> "${shutterRaw / 10000f}s"
                }
            } else "-"

            val iso = if (isoRaw != null && isoRaw > 0) "ISO $isoRaw" else "-"

            CameraExposureInfo(aperture, shutter, iso, battery)
        }
    }

    fun close() {
        synchronized(usbLock) {
            if (isSessionOpen && connection != null && endpointOut != null) {
                try {
                    // Send CloseSession (opcode: 0x1003)
                    val tx = transactionId++
                    val cmd = buildCommandContainer(0x1003, tx, 1)
                    connection!!.bulkTransfer(endpointOut, cmd, cmd.size, 500)
                } catch (e: Exception) {
                    // ignore
                }
            }

            isSessionOpen = false
            try {
                if (usbInterface != null && connection != null) {
                    connection!!.releaseInterface(usbInterface)
                }
            } catch (e: Exception) {
                // ignore
            }

            try {
                connection?.close()
            } catch (e: Exception) {
                // ignore
            }

            connection = null
            usbInterface = null
            endpointIn = null
            endpointOut = null
        }
    }

    fun getStorageIDs(): List<Int> {
        return synchronized(usbLock) {
            if (connection == null || endpointOut == null || endpointIn == null || !isSessionOpen) {
                return emptyList()
            }
            try {
                val tx = transactionId++
                // GetStorageIDs (0x1004)
                val cmd = buildCommandContainer(0x1004, tx)
                val sent = connection!!.bulkTransfer(endpointOut, cmd, cmd.size, 1000)
                if (sent < 0) return emptyList()

                val dataBuffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
                val readBytes = connection!!.bulkTransfer(endpointIn, dataBuffer.array(), dataBuffer.capacity(), 2000)
                if (readBytes < 12) return emptyList()

                dataBuffer.limit(readBytes)
                val length = dataBuffer.int
                val type = dataBuffer.short
                val code = dataBuffer.short
                val rTx = dataBuffer.int

                if (type != 2.toShort()) return emptyList()

                val numStorageIDs = dataBuffer.int
                val storageIDs = mutableListOf<Int>()
                for (i in 0 until numStorageIDs) {
                    if (dataBuffer.remaining() >= 4) {
                        storageIDs.add(dataBuffer.int)
                    }
                }
                readResponseContainer(tx)
                storageIDs
            } catch (e: Exception) {
                Log.e(tag, "Failed to get storage IDs", e)
                emptyList()
            }
        }
    }

    fun getAllObjectHandles(): List<Int> {
        return synchronized(usbLock) {
            if (connection == null || endpointOut == null || endpointIn == null || !isSessionOpen) {
                return emptyList()
            }
            
            val allHandles = mutableSetOf<Int>()
            // Query dual SD card storage IDs first to ensure both Slot 1 and Slot 2 are queried
            val storageIDs = getStorageIDs()
            val targetIDs = if (storageIDs.isNotEmpty()) storageIDs else listOf(0xFFFFFFFF.toInt())

            for (storageId in targetIDs) {
                try {
                    val tx = transactionId++
                    // GetObjectHandles (0x1007), params: StorageID, FormatCode (0), Parent (0)
                    val cmd = buildCommandContainer(0x1007, tx, storageId, 0, 0)
                    val sent = connection!!.bulkTransfer(endpointOut, cmd, cmd.size, 1000)
                    if (sent >= 0) {
                        val dataBuffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
                        val readBytes = connection!!.bulkTransfer(endpointIn, dataBuffer.array(), dataBuffer.capacity(), 2000)
                        if (readBytes >= 12) {
                            dataBuffer.limit(readBytes)
                            val length = dataBuffer.int
                            val type = dataBuffer.short
                            val code = dataBuffer.short
                            val rTx = dataBuffer.int

                            if (type == 2.toShort()) {
                                val numHandles = dataBuffer.int
                                for (i in 0 until numHandles) {
                                    if (dataBuffer.remaining() >= 4) {
                                        allHandles.add(dataBuffer.int)
                                    }
                                }
                            }
                        }
                        readResponseContainer(tx)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to get object handles for storage $storageId", e)
                }
            }

            allHandles.toList()
        }
    }

    fun getObjectBytes(handle: Int): ByteArray? {
        return synchronized(usbLock) {
            if (connection == null || endpointOut == null || endpointIn == null || !isSessionOpen) {
                return null
            }
            try {
                val tx = transactionId++
                // GetObject (0x1009), param: ObjectHandle
                val cmd = buildCommandContainer(0x1009, tx, handle)
                val sent = connection!!.bulkTransfer(endpointOut, cmd, cmd.size, 1000)
                if (sent < 0) return null

                // Read the header first
                val headerBuffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                val headerRead = connection!!.bulkTransfer(endpointIn, headerBuffer.array(), 12, 2000)
                if (headerRead < 12) {
                    Log.e(tag, "getObjectBytes: header read too short ($headerRead)")
                    return null
                }

                logPtpPacket("IN (Data-ObjectHeader)", headerBuffer.array(), headerRead)

                val totalLength = headerBuffer.int
                val type = headerBuffer.short
                val code = headerBuffer.short
                val rTx = headerBuffer.int

                if (type != 2.toShort()) { // Data container
                    return null
                }

                val dataSize = totalLength - 12
                if (dataSize <= 0 || dataSize > 12 * 1024 * 1024) { // Cap at 12MB to prevent memory issues
                    return null
                }

                val outBytes = ByteArray(dataSize)
                var bytesRead = 0
                val tempBuffer = ByteArray(128 * 1024) // Read in 128KB chunks

                while (bytesRead < dataSize) {
                    val toRead = minOf(tempBuffer.size, dataSize - bytesRead)
                    val chunkRead = connection!!.bulkTransfer(endpointIn, tempBuffer, toRead, 3000)
                    if (chunkRead <= 0) {
                        break
                    }
                    System.arraycopy(tempBuffer, 0, outBytes, bytesRead, chunkRead)
                    bytesRead += chunkRead
                }

                // Consume the trailing response
                readResponseContainer(tx)

                if (bytesRead > 0) {
                    if (bytesRead < dataSize) {
                        outBytes.copyOf(bytesRead)
                    } else {
                        outBytes
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to get object bytes for handle: $handle", e)
                null
            }
        }
    }

    private fun initSession(): Boolean {
        if (connection == null || endpointOut == null || endpointIn == null) return false
        val tx = transactionId++
        // InitSession (opcode: 0x1002, parameter: SessionID = 1)
        val cmd = buildCommandContainer(0x1002, tx, 1)
        val sent = connection!!.bulkTransfer(endpointOut, cmd, cmd.size, 1000)
        if (sent < 0) return false

        val response = readResponseContainer(tx)
        return response != null && response.responseCode == 0x2001.toShort() // PTP_RC_OK
    }

    private fun logPtpPacket(direction: String, data: ByteArray, length: Int) {
        if (length < 12) {
            val hexData = data.take(length).joinToString(" ") { String.format("%02X", it) }
            Log.d(tag, "PTP Packet ($direction): [Too short: $length bytes] raw=[$hexData]")
            return
        }
        val buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val len = buffer.int
        val type = buffer.short
        val code = buffer.short
        val tx = buffer.int
        
        val typeStr = when (type.toInt()) {
            1 -> "Command"
            2 -> "Data"
            3 -> "Response"
            4 -> "Event"
            else -> "Unknown ($type)"
        }
        
        val payloadLen = length - 12
        val hexPayload = if (payloadLen > 0) {
            val count = if (payloadLen > 16) 16 else payloadLen
            val slice = data.sliceArray(12 until (12 + count))
            val sliceStr = slice.joinToString(" ") { String.format("%02X", it) }
            " Payload($payloadLen bytes)=[$sliceStr${if (payloadLen > 16) " ..." else ""}]"
        } else ""
        
        Log.d(tag, "PTP Packet ($direction): Type=$typeStr, Code=0x${String.format("%04X", code)}, Tx=$tx, DeclaredLength=$len, ReadLength=$length$hexPayload")
    }

    private fun buildCommandContainer(opCode: Short, tx: Int, vararg params: Int): ByteArray {
        val headerSize = 12
        val paramSize = params.size * 4
        val totalSize = headerSize + paramSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(totalSize)
        buffer.putShort(1) // Command container type
        buffer.putShort(opCode)
        buffer.putInt(tx)
        for (p in params) {
            buffer.putInt(p)
        }
        val array = buffer.array()
        logPtpPacket("OUT (Command)", array, array.size)
        return array
    }

    private fun readResponseContainer(expectedTx: Int): PtpResponse? {
        val conn = connection ?: return null
        val epIn = endpointIn ?: return null

        val buffer = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
        val readBytes = conn.bulkTransfer(epIn, buffer.array(), buffer.capacity(), 1000)
        if (readBytes < 0) {
            Log.e(tag, "readResponseContainer: bulkTransfer returned error $readBytes")
            return null
        }
        if (readBytes < 12) {
            Log.e(tag, "readResponseContainer: read too few bytes ($readBytes)")
            return null
        }

        logPtpPacket("IN (Response)", buffer.array(), readBytes)

        buffer.limit(readBytes)
        val length = buffer.int
        val type = buffer.short
        val code = buffer.short
        val tx = buffer.int

        if (type != 3.toShort()) { // Response container type
            Log.w(tag, "readResponseContainer: expected type 3 (Response), got type $type")
            return null
        }

        return PtpResponse(code, tx)
    }

    /**
     * Set a device property using SetDevicePropValue (0x1016)
     */
    fun setDeviceProperty(propCode: Short, valueBytes: ByteArray): Boolean {
        return synchronized(usbLock) {
            val conn = connection ?: return false
            val epOut = endpointOut ?: return false
            if (!isSessionOpen) return false

            try {
                val tx = transactionId++
                // SetDevicePropValue (0x1016), parameter: propCode
                val cmd = buildCommandContainer(0x1016, tx, propCode.toInt())
                val sent = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
                if (sent < 0) return false

                // Data container header
                val dataHeader = ByteBuffer.allocate(12 + valueBytes.size).order(ByteOrder.LITTLE_ENDIAN)
                dataHeader.putInt(12 + valueBytes.size) // length
                dataHeader.putShort(2) // Type: Data
                dataHeader.putShort(0x1016) // Code: SetDevicePropValue
                dataHeader.putInt(tx) // TransactionId
                dataHeader.put(valueBytes)

                val dataSent = conn.bulkTransfer(epOut, dataHeader.array(), dataHeader.capacity(), 1000)
                if (dataSent < 0) return false

                val response = readResponseContainer(tx)
                response != null && response.responseCode == 0x2001.toShort() // PTP_RC_OK
            } catch (e: Exception) {
                Log.e(tag, "Failed to set device property 0x${propCode.toString(16)}", e)
                false
            }
        }
    }

    fun setIso(isoValue: Int): Boolean {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(isoValue.toShort())
        return setDeviceProperty(0x500F.toShort(), buffer.array())
    }

    fun setAperture(fValueRaw: Int): Boolean {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(fValueRaw.toShort())
        return setDeviceProperty(0x5007.toShort(), buffer.array())
    }

    fun setShutterSpeed(shutterRaw: Int): Boolean {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(shutterRaw)
        return setDeviceProperty(0x500D.toShort(), buffer.array())
    }

    fun setExposureCompensation(biasRaw: Short): Boolean {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(biasRaw)
        return setDeviceProperty(0x5010.toShort(), buffer.array())
    }

    fun setWhiteBalance(wbRaw: Short): Boolean {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(wbRaw)
        return setDeviceProperty(0x500A.toShort(), buffer.array())
    }

    /**
     * Poll the USB Interrupt endpoint for MTP/PTP events (like Shutter Release/ObjectAdded)
     * Returns the ObjectHandle of the new image, or null if no event.
     */
    fun checkMtpEvent(): Int? {
        return synchronized(usbLock) {
            val conn = connection ?: return null
            val epIntr = endpointIntr ?: return null

            val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            // Non-blocking bulkTransfer with a very short timeout
            val readBytes = conn.bulkTransfer(epIntr, buffer.array(), buffer.capacity(), 50)
            if (readBytes >= 12) {
                buffer.limit(readBytes)
                val length = buffer.int
                val type = buffer.short
                val code = buffer.short
                val transactionId = buffer.int

                if (code == 0x4002.toShort()) { // PTP_EC_ObjectAdded
                    if (buffer.remaining() >= 4) {
                        val handle = buffer.int
                        Log.d(tag, "Detected MTP event: ObjectAdded with handle $handle")
                        return handle
                    }
                }
            }
            null
        }
    }

    private class PtpResponse(val responseCode: Short, val transactionId: Int)
}

data class CameraExposureInfo(
    val aperture: String,
    val shutter: String,
    val iso: String,
    val battery: String
)
