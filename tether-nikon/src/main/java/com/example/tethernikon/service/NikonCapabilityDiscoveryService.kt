package com.example.tethernikon.service

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

class NikonCapabilityDiscoveryService(private val context: Context) {
    private val tag = "NikonDiscoveryService"

    data class NikonDiscoveryResult(
        val isCompatible: Boolean,
        val manufacturer: String = "Unknown",
        val model: String = "Unknown",
        val serialNumber: String = "",
        val supportedOps: List<Int> = emptyList(),
        val isNikonDevice: Boolean = false,
        val errorReason: String? = null
    )

    fun discoverCapabilities(device: UsbDevice): NikonDiscoveryResult {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return NikonDiscoveryResult(false, errorReason = "USB Manager not available")

        if (!usbManager.hasPermission(device)) {
            return NikonDiscoveryResult(false, errorReason = "USB permission not granted")
        }

        val connection = usbManager.openDevice(device)
            ?: return NikonDiscoveryResult(false, errorReason = "Failed to open UsbDeviceConnection")

        var usbInterface: UsbInterface? = null
        try {
            // Find suitable interface (Class 6 Still Image, or vendor-specific)
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

            if (foundInterface == null && device.interfaceCount > 0) {
                foundInterface = device.getInterface(0)
            }

            if (foundInterface == null) {
                return NikonDiscoveryResult(false, errorReason = "No suitable USB interface found")
            }

            usbInterface = foundInterface
            if (!connection.claimInterface(usbInterface, true)) {
                return NikonDiscoveryResult(false, errorReason = "Failed to claim USB interface")
            }

            // Locate bulk endpoints
            var endpointIn: UsbEndpoint? = null
            var endpointOut: UsbEndpoint? = null
            for (j in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        endpointIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        endpointOut = ep
                    }
                }
            }

            if (endpointIn == null || endpointOut == null) {
                return NikonDiscoveryResult(false, errorReason = "Bulk endpoints not found")
            }

            // Send standard PTP GetDeviceInfo command (Opcode: 0x1001, Tx: 0)
            val tx = 0
            val cmd = buildCommandContainer(0x1001, tx)
            val sent = connection.bulkTransfer(endpointOut, cmd, cmd.size, 1000)
            if (sent < 0) {
                return NikonDiscoveryResult(false, errorReason = "Failed to send GetDeviceInfo command")
            }

            // Read the data container response
            val dataBuffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            val readBytes = connection.bulkTransfer(endpointIn, dataBuffer.array(), dataBuffer.capacity(), 1500)
            if (readBytes < 12) {
                return NikonDiscoveryResult(false, errorReason = "Failed to read DeviceInfo response (read $readBytes bytes)")
            }

            dataBuffer.limit(readBytes)
            val length = dataBuffer.int
            val type = dataBuffer.short
            val code = dataBuffer.short
            val rTx = dataBuffer.int

            if (type != 2.toShort()) { // Type 2 is Data container
                return NikonDiscoveryResult(false, errorReason = "Unexpected response container type: $type")
            }

            // Parse PTP DeviceInfo dataset
            val infoResult = parseDeviceInfo(dataBuffer)

            // Read PTP Response container (Type 3) to clear the pipe
            val respBuffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            connection.bulkTransfer(endpointIn, respBuffer.array(), respBuffer.capacity(), 500)

            val isNikon = device.vendorId == 1200 || device.vendorId == 0x04B0 ||
                          infoResult.manufacturer.contains("Nikon", ignoreCase = true) ||
                          infoResult.model.contains("Nikon", ignoreCase = true)

            // Check compatibility based on vendor or operations support:
            // Standard capture & object querying operations
            val hasCaptureOp = infoResult.supportedOps.contains(0x100E) // InitiateCapture
            val isCompatible = isNikon || hasCaptureOp || infoResult.supportedOps.contains(0x1009)

            return NikonDiscoveryResult(
                isCompatible = isCompatible,
                manufacturer = infoResult.manufacturer,
                model = infoResult.model,
                serialNumber = infoResult.serialNumber,
                supportedOps = infoResult.supportedOps,
                isNikonDevice = isNikon
            )

        } catch (e: Exception) {
            Log.e(tag, "Exception during PTP discovery", e)
            return NikonDiscoveryResult(false, errorReason = "Exception: ${e.message}")
        } finally {
            try {
                if (usbInterface != null) {
                    connection.releaseInterface(usbInterface)
                }
            } catch (e: Exception) {
                // ignore
            }
            try {
                connection.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun buildCommandContainer(opCode: Short, tx: Int): ByteArray {
        val totalSize = 12
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(totalSize)
        buffer.putShort(1) // Command container type
        buffer.putShort(opCode)
        buffer.putInt(tx)
        return buffer.array()
    }

    private class DeviceInfoParsed(
        val manufacturer: String,
        val model: String,
        val serialNumber: String,
        val supportedOps: List<Int>
    )

    private fun parseDeviceInfo(buffer: ByteBuffer): DeviceInfoParsed {
        var manufacturer = "Unknown"
        var model = "Unknown"
        var serialNumber = ""
        var ops: List<Int> = emptyList()

        try {
            if (buffer.remaining() >= 2) {
                val standardVersion = buffer.short
            }
            if (buffer.remaining() >= 4) {
                val vendorExtensionId = buffer.int
            }
            if (buffer.remaining() >= 2) {
                val vendorExtensionVersion = buffer.short
            }
            // Vendor Extension Description
            readPtpString(buffer)

            if (buffer.remaining() >= 2) {
                val functionalMode = buffer.short
            }

            // Operations Supported (Array of Uint16)
            ops = readPtpUint16Array(buffer)

            // Events Supported (Array of Uint16)
            readPtpUint16Array(buffer)

            // Device Properties Supported (Array of Uint16)
            readPtpUint16Array(buffer)

            // Capture Formats (Array of Uint16)
            readPtpUint16Array(buffer)

            // Image Formats (Array of Uint16)
            readPtpUint16Array(buffer)

            // Manufacturer String
            val mfg = readPtpString(buffer)
            if (mfg.isNotEmpty()) manufacturer = mfg

            // Model String
            val mdl = readPtpString(buffer)
            if (mdl.isNotEmpty()) model = mdl

            // Device Version String
            readPtpString(buffer)

            // Serial Number String
            val sn = readPtpString(buffer)
            if (sn.isNotEmpty()) serialNumber = sn

        } catch (e: Exception) {
            Log.e(tag, "Error parsing PTP device info", e)
        }

        return DeviceInfoParsed(manufacturer, model, serialNumber, ops)
    }

    private fun readPtpString(buffer: ByteBuffer): String {
        if (buffer.remaining() < 1) return ""
        val len = buffer.get().toInt() and 0xFF
        if (len == 0) return ""
        val chars = CharArray(len - 1)
        for (i in 0 until (len - 1)) {
            if (buffer.remaining() >= 2) {
                chars[i] = buffer.char
            }
        }
        if (buffer.remaining() >= 2) {
            buffer.char // consume null terminator
        }
        return String(chars).trim()
    }

    private fun readPtpUint16Array(buffer: ByteBuffer): List<Int> {
        if (buffer.remaining() < 4) return emptyList()
        val count = buffer.int
        if (count <= 0 || count > 1000) return emptyList()
        val list = ArrayList<Int>(count)
        for (i in 0 until count) {
            if (buffer.remaining() >= 2) {
                list.add(buffer.short.toInt() and 0xFFFF)
            }
        }
        return list
    }
}
