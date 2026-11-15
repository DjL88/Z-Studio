package com.example.tethernikon.data

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB Host (OTG) PTP/MTP Camera Connector for Nikon (Z8, Z5ii, Z6/Z7 series) over direct USB-C.
 *
 * Implements direct USB transfers via android.hardware.usb:
 * - Claim Still Image interface (Class 6, Subclass 1, Protocol 1)
 * - Assign Bulk IN, Bulk OUT, and Interrupt IN endpoints
 * - Listen on Interrupt IN endpoint in background coroutine to emit PTP events (ObjectAdded 0x4002 & Nikon ObjectAddedInSDRAM 0xC101)
 * - Thread-safe PTP command execution (GetObjectInfo 0x1008 & GetObject 0x1009) via Bulk OUT/IN
 * - Strips PTP transaction headers from data phase for uncorrupted JPEG byte extraction
 */
class UsbCameraConnector(
    private val context: Context,
    private val device: UsbDevice
) {
    private val tag = "UsbCameraConnector"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null      // Bulk IN
    private var endpointOut: UsbEndpoint? = null     // Bulk OUT
    private var endpointIntr: UsbEndpoint? = null    // Interrupt IN

    private var transactionId = 1
    @Volatile private var isSessionOpen = false
    private val usbLock = Any()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var eventListenerJob: Job? = null

    sealed class PtpUsbEvent {
        data class ObjectAdded(val objectHandle: Int, val isSdram: Boolean) : PtpUsbEvent()
        data class UnknownEvent(val eventCode: Int, val param1: Int) : PtpUsbEvent()
        data class ConnectionError(val message: String) : PtpUsbEvent()
    }

    private val _eventFlow = MutableSharedFlow<PtpUsbEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<PtpUsbEvent> = _eventFlow.asSharedFlow()

    data class ObjectInfo(
        val objectHandle: Int,
        val storageId: Int,
        val objectFormat: Short,
        val objectCompressedSize: Int,
        val fileName: String
    )

    data class DevicePropDesc(
        val propertyCode: Short,
        val dataType: Short,
        val getSet: Byte,
        val defaultValue: Int,
        val currentValue: Int,
        val formFlag: Byte,
        val enumSupportedValues: List<Int> = emptyList()
    )

    companion object {
        // PTP Container Types
        const val CONTAINER_COMMAND = 1.toShort()
        const val CONTAINER_DATA = 2.toShort()
        const val CONTAINER_RESPONSE = 3.toShort()
        const val CONTAINER_EVENT = 4.toShort()

        // PTP Opcodes
        const val OP_INIT_SESSION = 0x1002.toShort()
        const val OP_CLOSE_SESSION = 0x1003.toShort()
        const val OP_GET_STORAGE_IDS = 0x1004.toShort()
        const val OP_GET_OBJECT_HANDLES = 0x1007.toShort()
        const val OP_GET_OBJECT_INFO = 0x1008.toShort()
        const val OP_GET_OBJECT = 0x1009.toShort()
        const val OP_INITIATE_CAPTURE = 0x100E.toShort()
        const val OP_GET_DEVICE_PROP_DESC = 0x1014.toShort()
        const val OP_GET_DEVICE_PROP_VALUE = 0x1015.toShort()
        const val OP_SET_DEVICE_PROP_VALUE = 0x1016.toShort()

        // PTP Device Property Codes (Standard PIMA 15740 / Nikon Z8 & Z5ii)
        const val PROP_BATTERY_LEVEL = 0x5001.toShort() // UINT8 0-100%
        const val PROP_WHITE_BALANCE = 0x5005.toShort() // UINT16 Enum
        const val PROP_APERTURE = 0x5007.toShort()      // UINT16 (f-stop * 100)
        const val PROP_SHUTTER_SPEED = 0x500D.toShort() // UINT32 (Exposure time in 10,000ths sec)
        const val PROP_ISO = 0x500F.toShort()           // UINT16 (Exposure index)
        const val PROP_EXPOSURE_BIAS = 0x5010.toShort() // INT16 (Millistops, e.g. +1000 = +1.0 EV)

        // Response Codes
        const val RESP_OK = 0x2001.toShort()

        // Event Codes
        const val EVENT_OBJECT_ADDED = 0x4002 // Standard PTP ObjectAdded
        const val NIKON_EVENT_OBJECT_ADDED_IN_SDRAM = 0xC101 // Nikon Z8/Z5ii SDRAM Tether Event
    }

    /**
     * Opens USB device connection, claims interface (Class 6, Subclass 1, Protocol 1),
     * locates endpoints, opens PTP session, and starts Interrupt IN event listener.
     */
    fun openSession(): Boolean {
        synchronized(usbLock) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
            if (!usbManager.hasPermission(device)) {
                Log.e(tag, "No permission to open USB device: ${device.deviceName}")
                return false
            }

            val conn = usbManager.openDevice(device)
            if (conn == null) {
                Log.e(tag, "Failed to open UsbDeviceConnection for device: ${device.deviceName}")
                return false
            }
            connection = conn

            // Claim PTP / Still Image interface (Class 6, Subclass 1, Protocol 1) or vendor fallback
            var foundInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE ||
                    intf.interfaceClass == 6 ||
                    intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
                ) {
                    foundInterface = intf
                    break
                }
            }

            if (foundInterface == null && device.interfaceCount > 0) {
                foundInterface = device.getInterface(0)
            }

            if (foundInterface == null) {
                Log.e(tag, "No suitable USB interface found on device ${device.deviceName}")
                close()
                return false
            }

            usbInterface = foundInterface
            if (!conn.claimInterface(usbInterface, true)) {
                Log.e(tag, "Failed to claim USB interface")
                close()
                return false
            }

            // Identify endpoints: Bulk IN, Bulk OUT, Interrupt IN
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
                Log.e(tag, "Bulk IN or Bulk OUT endpoint not found")
                close()
                return false
            }

            transactionId = 1
            isSessionOpen = false

            // Send InitSession opcode (0x1002, SessionID = 1)
            val initSuccess = initSessionInternal()
            if (!initSuccess) {
                Log.e(tag, "InitSession failed")
                close()
                return false
            }

            isSessionOpen = true
            Log.d(tag, "PTP USB Session successfully established with device: ${device.deviceName}")

            // Start background Coroutine listener on Interrupt IN endpoint
            startEventListenerThread()

            return true
        }
    }

    /**
     * Background listener thread reading Interrupt IN endpoint for PTP Events.
     */
    private fun startEventListenerThread() {
        eventListenerJob = scope.launch {
            Log.d(tag, "Interrupt IN Event listener started.")
            val conn = connection ?: return@launch
            val epIntr = endpointIntr

            if (epIntr == null) {
                Log.w(tag, "Interrupt IN endpoint not available, event polling disabled.")
                return@launch
            }

            val maxPacketSize = epIntr.maxPacketSize.coerceAtLeast(64)
            val buffer = ByteArray(maxPacketSize)

            while (isActive && isSessionOpen) {
                try {
                    val readBytes = conn.bulkTransfer(epIntr, buffer, buffer.size, 1000)
                    if (readBytes >= 12) {
                        val buf = ByteBuffer.wrap(buffer, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val containerLen = buf.int
                        val containerType = buf.short
                        val eventCode = buf.short.toInt() and 0xFFFF
                        val txId = buf.int
                        val param1 = if (buf.remaining() >= 4) buf.int else 0

                        if (containerType == CONTAINER_EVENT) {
                            Log.d(tag, "USB Interrupt Event Received -> Code: 0x${eventCode.toString(16).uppercase()}, Param1: 0x${param1.toString(16).uppercase()}")
                            when (eventCode) {
                                EVENT_OBJECT_ADDED -> {
                                    Log.d(tag, "Standard ObjectAdded (0x4002) Event! Handle: $param1")
                                    _eventFlow.emit(PtpUsbEvent.ObjectAdded(objectHandle = param1, isSdram = false))
                                }
                                NIKON_EVENT_OBJECT_ADDED_IN_SDRAM -> {
                                    Log.d(tag, "Nikon ObjectAddedInSDRAM (0xC101) Event! Handle: $param1")
                                    _eventFlow.emit(PtpUsbEvent.ObjectAdded(objectHandle = param1, isSdram = true))
                                }
                                else -> {
                                    _eventFlow.emit(PtpUsbEvent.UnknownEvent(eventCode, param1))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isSessionOpen) {
                        Log.e(tag, "Error reading from Interrupt IN endpoint", e)
                        _eventFlow.emit(PtpUsbEvent.ConnectionError(e.message ?: "Interrupt IN error"))
                    }
                    break
                }
            }
        }
    }

    /**
     * Get Object Info (0x1008) via Bulk OUT/IN.
     */
    suspend fun getObjectInfo(objectHandle: Int): ObjectInfo? = withContext(Dispatchers.IO) {
        synchronized(usbLock) {
            val conn = connection ?: return@withContext null
            val epOut = endpointOut ?: return@withContext null
            val epIn = endpointIn ?: return@withContext null
            if (!isSessionOpen) return@withContext null

            try {
                val tx = transactionId++
                val cmd = buildCommandContainer(OP_GET_OBJECT_INFO, tx, objectHandle)
                val sent = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
                if (sent < 0) return@withContext null

                // Read Data Container
                val buffer = ByteArray(1024)
                val readBytes = conn.bulkTransfer(epIn, buffer, buffer.size, 2000)
                if (readBytes < 12) return@withContext null

                val buf = ByteBuffer.wrap(buffer, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN)
                val length = buf.int
                val type = buf.short
                val code = buf.short
                val rTx = buf.int

                if (type != CONTAINER_DATA) return@withContext null

                val storageId = buf.int
                val objFormat = buf.short
                val objProtection = buf.short
                val objCompressedSize = buf.int

                var fileName = String.format("DSC_%04d.JPG", Math.abs(objectHandle) % 10000)

                // Skip fixed fields to locate String
                if (buf.remaining() > 40) {
                    try {
                        buf.position(buf.position() + 32)
                        if (buf.remaining() > 2) {
                            val strLen = buf.get().toInt() and 0xFF
                            if (strLen > 0 && buf.remaining() >= strLen * 2) {
                                val chars = CharArray(strLen)
                                for (i in 0 until strLen) {
                                    chars[i] = buf.char
                                }
                                val parsedName = String(chars).trimEnd('\u0000')
                                if (parsedName.isNotEmpty()) {
                                    fileName = parsedName
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Error parsing filename string in ObjectInfo dataset", e)
                    }
                }

                // Consume response packet
                readResponseContainer(tx)

                ObjectInfo(
                    objectHandle = objectHandle,
                    storageId = storageId,
                    objectFormat = objFormat,
                    objectCompressedSize = objCompressedSize,
                    fileName = fileName
                )
            } catch (e: Exception) {
                Log.e(tag, "getObjectInfo failed for handle $objectHandle", e)
                null
            }
        }
    }

    /**
     * Get Object (0x1009) image bytes via Bulk OUT/IN.
     * Strips 12-byte PTP Data Container Header so returned ByteArray is clean JPEG.
     */
    suspend fun getObject(objectHandle: Int): ByteArray? = withContext(Dispatchers.IO) {
        synchronized(usbLock) {
            val conn = connection ?: return@withContext null
            val epOut = endpointOut ?: return@withContext null
            val epIn = endpointIn ?: return@withContext null
            if (!isSessionOpen) return@withContext null

            try {
                val tx = transactionId++
                val cmd = buildCommandContainer(OP_GET_OBJECT, tx, objectHandle)
                val sent = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
                if (sent < 0) return@withContext null

                // FIX 1: Max safe buffer size for Android USB Bulk Transfers is 16KB.
                val bufferSize = 16384
                val initialBuffer = ByteArray(bufferSize)
                val readBytes = conn.bulkTransfer(epIn, initialBuffer, bufferSize, 4000)
                if (readBytes < 12) {
                    Log.e(tag, "getObject: failed to read data container header ($readBytes bytes)")
                    return@withContext null
                }

                val headerBuf = ByteBuffer.wrap(initialBuffer, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
                val totalLength = headerBuf.int
                val containerType = headerBuf.short
                val code = headerBuf.short
                val txId = headerBuf.int

                if (containerType != CONTAINER_DATA) {
                    Log.e(tag, "getObject: expected Data container (2), got $containerType")
                    return@withContext null
                }

                val dataSize = totalLength - 12
                if (dataSize <= 0) return@withContext null

                val byteStream = java.io.ByteArrayOutputStream(dataSize)

                // Strip the 12-byte header: payload starting at offset 12 is JPEG data
                val firstChunkPayloadLen = readBytes - 12
                if (firstChunkPayloadLen > 0) {
                    byteStream.write(initialBuffer, 12, minOf(firstChunkPayloadLen, dataSize))
                }

                val chunkBuffer = ByteArray(bufferSize)
                while (byteStream.size() < dataSize) {
                    val remainingNeeded = dataSize - byteStream.size()
                    
                    // FIX 2: ALWAYS request the full chunkBuffer.size (16384) from the kernel.
                    // Never pass `remainingNeeded` as the length argument to prevent Babble overflow.
                    val chunkRead = conn.bulkTransfer(epIn, chunkBuffer, chunkBuffer.size, 4000)
                    if (chunkRead <= 0) {
                        Log.w(tag, "getObject: timeout reading bulk data chunk ($chunkRead)")
                        break
                    }
                    
                    // Only write the bytes that actually belong to the image to the stream
                    val bytesToAppend = minOf(chunkRead, remainingNeeded)
                    byteStream.write(chunkBuffer, 0, bytesToAppend)
                }

                // Consume trailing response container
                readResponseContainer(tx)

                val rawBytes = byteStream.toByteArray()
                Log.d(tag, "getObject: successfully downloaded ${rawBytes.size} bytes for handle $objectHandle")
                rawBytes
            } catch (e: Exception) {
                Log.e(tag, "getObject failed for handle $objectHandle", e)
                null
            }
        }
    }

    /**
     * Get Device Property Description (0x1014) to retrieve current value, defaults, and allowed value list.
     */
    suspend fun getDevicePropDesc(propCode: Short): DevicePropDesc? = withContext(Dispatchers.IO) {
        synchronized(usbLock) {
            val conn = connection ?: return@withContext null
            val epOut = endpointOut ?: return@withContext null
            val epIn = endpointIn ?: return@withContext null
            if (!isSessionOpen) return@withContext null

            try {
                val tx = transactionId++
                val cmd = buildCommandContainer(OP_GET_DEVICE_PROP_DESC, tx, propCode.toInt() and 0xFFFF)
                val sent = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
                if (sent < 0) return@withContext null

                val buffer = ByteArray(1024)
                val readBytes = conn.bulkTransfer(epIn, buffer, buffer.size, 2000)
                if (readBytes < 12) return@withContext null

                val buf = ByteBuffer.wrap(buffer, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN)
                val length = buf.int
                val type = buf.short
                val code = buf.short
                val rTx = buf.int

                if (type != CONTAINER_DATA) return@withContext null

                val propertyCode = buf.short
                val dataType = buf.short
                val getSet = buf.get()

                fun readPropVal(type: Short): Int {
                    return when (type.toInt() and 0xFFFF) {
                        0x0001, 0x0002 -> buf.get().toInt() and 0xFF
                        0x0003, 0x0004 -> buf.short.toInt() and 0xFFFF
                        0x0005, 0x0006 -> buf.int
                        else -> if (buf.remaining() >= 2) buf.short.toInt() and 0xFFFF else 0
                    }
                }

                val factoryDefault = readPropVal(dataType)
                val currentValue = readPropVal(dataType)
                val formFlag = if (buf.hasRemaining()) buf.get() else 0.toByte()

                val enumValues = mutableListOf<Int>()
                if (formFlag.toInt() == 2 && buf.remaining() >= 2) {
                    val numValues = buf.short.toInt() and 0xFFFF
                    for (i in 0 until numValues) {
                        if (!buf.hasRemaining()) break
                        enumValues.add(readPropVal(dataType))
                    }
                }

                readResponseContainer(tx)

                DevicePropDesc(
                    propertyCode = propertyCode,
                    dataType = dataType,
                    getSet = getSet,
                    defaultValue = factoryDefault,
                    currentValue = currentValue,
                    formFlag = formFlag,
                    enumSupportedValues = enumValues
                )
            } catch (e: Exception) {
                Log.e(tag, "getDevicePropDesc failed for 0x${propCode.toString(16)}", e)
                null
            }
        }
    }

    /**
     * Get Device Property Value (0x1015).
     */
    suspend fun getDevicePropValue(propCode: Short): Int? = withContext(Dispatchers.IO) {
        val desc = getDevicePropDesc(propCode)
        desc?.currentValue
    }

    /**
     * Set Device Property Value (0x1016) sending GetDevicePropDesc verified values via Data Phase.
     */
    suspend fun setDevicePropValue(propCode: Short, value: Int, dataTypeSize: Int = 2): Boolean = withContext(Dispatchers.IO) {
        synchronized(usbLock) {
            val conn = connection ?: return@withContext false
            val epOut = endpointOut ?: return@withContext false
            val epIn = endpointIn ?: return@withContext false
            if (!isSessionOpen) return@withContext false

            try {
                val tx = transactionId++
                // Command Phase
                val cmd = buildCommandContainer(OP_SET_DEVICE_PROP_VALUE, tx, propCode.toInt() and 0xFFFF)
                val sentCmd = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
                if (sentCmd < 0) return@withContext false

                // Data Phase
                val dataHeaderLen = 12
                val totalDataLen = dataHeaderLen + dataTypeSize
                val dataBuf = ByteBuffer.allocate(totalDataLen).order(ByteOrder.LITTLE_ENDIAN)
                dataBuf.putInt(totalDataLen)
                dataBuf.putShort(CONTAINER_DATA)
                dataBuf.putShort(OP_SET_DEVICE_PROP_VALUE)
                dataBuf.putInt(tx)

                when (dataTypeSize) {
                    1 -> dataBuf.put(value.toByte())
                    2 -> dataBuf.putShort(value.toShort())
                    4 -> dataBuf.putInt(value)
                    else -> dataBuf.putShort(value.toShort())
                }

                val sentData = conn.bulkTransfer(epOut, dataBuf.array(), dataBuf.capacity(), 1000)
                if (sentData < 0) return@withContext false

                val response = readResponseContainer(tx)
                val success = response != null && response.responseCode == RESP_OK
                Log.d(tag, "setDevicePropValue 0x${propCode.toString(16)} = $value -> success: $success")
                success
            } catch (e: Exception) {
                Log.e(tag, "setDevicePropValue failed for 0x${propCode.toString(16)} = $value", e)
                false
            }
        }
    }

    /**
     * Trigger Camera Capture / Shutter Release via InitiateCapture (0x100E).
     */
    suspend fun initiateCapture(): Boolean = withContext(Dispatchers.IO) {
        synchronized(usbLock) {
            val conn = connection ?: return@withContext false
            val epOut = endpointOut ?: return@withContext false
            if (!isSessionOpen) return@withContext false

            try {
                val tx = transactionId++
                // InitiateCapture parameter: StorageID (0x00000000 = default)
                val cmd = buildCommandContainer(OP_INITIATE_CAPTURE, tx, 0x00000000)
                val sent = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
                if (sent < 0) return@withContext false

                val resp = readResponseContainer(tx)
                val success = resp != null && resp.responseCode == RESP_OK
                Log.d(tag, "initiateCapture triggered -> success: $success")
                success
            } catch (e: Exception) {
                Log.e(tag, "initiateCapture failed", e)
                false
            }
        }
    }

    private fun initSessionInternal(): Boolean {
        val conn = connection ?: return false
        val epOut = endpointOut ?: return false
        val tx = transactionId++

        val cmd = buildCommandContainer(OP_INIT_SESSION, tx, 1)
        val sent = conn.bulkTransfer(epOut, cmd, cmd.size, 1000)
        if (sent < 0) return false

        val response = readResponseContainer(tx)
        
        // Accept both OK (0x2001) and SessionAlreadyOpen (0x201E) for session recovery
        return response != null && (response.responseCode == RESP_OK || response.responseCode == 0x201E.toShort())
    }

    private fun buildCommandContainer(opCode: Short, tx: Int, vararg params: Int): ByteArray {
        val headerSize = 12
        val paramSize = params.size * 4
        val totalSize = headerSize + paramSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(totalSize)
        buffer.putShort(CONTAINER_COMMAND)
        buffer.putShort(opCode)
        buffer.putInt(tx)
        for (p in params) {
            buffer.putInt(p)
        }
        return buffer.array()
    }

    private fun readResponseContainer(expectedTx: Int): PtpResponse? {
        val conn = connection ?: return null
        val epIn = endpointIn ?: return null

        val buffer = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
        val readBytes = conn.bulkTransfer(epIn, buffer.array(), buffer.capacity(), 1000)
        if (readBytes < 12) return null

        buffer.limit(readBytes)
        val length = buffer.int
        val type = buffer.short
        val code = buffer.short
        val tx = buffer.int

        if (type != CONTAINER_RESPONSE) return null

        return PtpResponse(code, tx)
    }

    private data class PtpResponse(val responseCode: Short, val transactionId: Int)

    fun close() {
        synchronized(usbLock) {
            isSessionOpen = false
            eventListenerJob?.cancel()

            try {
                if (usbInterface != null && connection != null) {
                    connection!!.releaseInterface(usbInterface)
                }
            } catch (_: Exception) {}

            try {
                connection?.close()
            } catch (_: Exception) {}

            connection = null
            usbInterface = null
            endpointIn = null
            endpointOut = null
            endpointIntr = null
        }
    }
}
