package com.example.tethernikon.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * PTP/IP Camera Connector for Nikon (Z8, Z5ii, Z6/Z7 series) over Wi-Fi (TCP Port 15740).
 *
 * Implements dual-socket PTP/IP architecture:
 * 1. Command Connection (Socket 1): Sends operations (Init_Command_Request, GetObjectInfo, GetObject).
 * 2. Event Connection (Socket 2): Listens for asynchronous event notifications (Init_Event_Request, Packet Type 8).
 */
class PtpIpCameraConnector(
    private val cameraIpAddress: String,
    private val port: Int = 15740,
    private val clientName: String = "AndroidStudioKiosk"
) {
    private val tag = "PtpIpCameraConnector"

    // Sockets & I/O Streams
    private var commandSocket: Socket? = null
    private var commandOut: DataOutputStream? = null
    private var commandIn: DataInputStream? = null

    private var eventSocket: Socket? = null
    private var eventOut: DataOutputStream? = null
    private var eventIn: DataInputStream? = null

    // Session State
    private var sessionId: Int = 0
    private var transactionId: Int = 1
    @Volatile private var isConnected = false

    // Coroutine Scope for Event Listener
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var eventListenerJob: Job? = null

    // Event Flow for downstream subscribers (e.g. ViewModel or Repository)
    private val _eventFlow = MutableSharedFlow<PtpIpEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<PtpIpEvent> = _eventFlow.asSharedFlow()

    sealed class PtpIpEvent {
        data class ObjectAdded(val objectHandle: Int, val isSdram: Boolean) : PtpIpEvent()
        data class UnknownEvent(val eventCode: Int, val param1: Int) : PtpIpEvent()
        data class ConnectionError(val message: String) : PtpIpEvent()
    }

    data class ObjectInfo(
        val objectHandle: Int,
        val storageId: Int,
        val objectFormat: Short,
        val objectCompressedSize: Int,
        val fileName: String
    )

    // --- PACKET TYPE CONSTANTS (CIPA DC-005-2005 / ISO 15740 PTP/IP) ---
    companion object {
        const val PKT_INIT_COMMAND_REQ = 1
        const val PKT_INIT_COMMAND_ACK = 2
        const val PKT_INIT_EVENT_REQ = 3
        const val PKT_INIT_EVENT_ACK = 4
        const val PKT_COMMAND_REQ = 5
        const val PKT_COMMAND_RESP = 6
        const val PKT_EVENT = 8

        // PTP Opcodes
        const val OP_OPEN_SESSION = 0x1002
        const val OP_CLOSE_SESSION = 0x1003
        const val OP_GET_OBJECT_INFO = 0x1008
        const val OP_GET_OBJECT = 0x1009

        // PTP Response Codes
        const val RESP_OK = 0x2001

        // Event Codes
        const val EVENT_OBJECT_ADDED = 0x4002 // Standard PTP ObjectAdded
        const val NIKON_EVENT_OBJECT_ADDED_IN_SDRAM = 0xC101 // Nikon Z8/Z5ii SDRAM Tether Event
    }

    /**
     * Connects to the camera:
     * Step 1: Open Command socket & send Init_Command_Request
     * Step 2: Read Init_Command_Ack & extract Session ID
     * Step 3: Open Event socket & send Init_Event_Request with Session ID
     * Step 4: Read Init_Event_Ack
     * Step 5: Start background event listener loop
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Connecting to camera at $cameraIpAddress:$port...")

            // --- STEP 1: Command Connection ---
            val cmdSock = Socket()
            cmdSock.connect(InetSocketAddress(cameraIpAddress, port), 5000)
            commandSocket = cmdSock
            commandOut = DataOutputStream(cmdSock.getOutputStream())
            commandIn = DataInputStream(cmdSock.getInputStream())

            // Send Init_Command_Request
            val clientGuid = UUID.randomUUID()
            val initCmdPacket = buildInitCommandRequestPacket(clientGuid, clientName)
            commandOut?.write(initCmdPacket)
            commandOut?.flush()

            // Receive Init_Command_Ack
            val ackHeader = readPacketHeader(commandIn!!)
            if (ackHeader.packetType != PKT_INIT_COMMAND_ACK) {
                Log.e(tag, "Expected Init_Command_Ack (2), got type ${ackHeader.packetType}")
                close()
                return@withContext false
            }

            val ackPayload = ByteArray(ackHeader.length - 8)
            commandIn?.readFully(ackPayload)
            val ackBuf = ByteBuffer.wrap(ackPayload).order(ByteOrder.LITTLE_ENDIAN)

            // --- STEP 2: Save Session ID ---
            sessionId = ackBuf.int
            Log.d(tag, "Received Session ID from Init_Command_Ack: $sessionId")

            // --- STEP 3: Open Event Connection ---
            val evtSock = Socket()
            evtSock.connect(InetSocketAddress(cameraIpAddress, port), 5000)
            eventSocket = evtSock
            eventOut = DataOutputStream(evtSock.getOutputStream())
            eventIn = DataInputStream(evtSock.getInputStream())

            // Send Init_Event_Request using Session ID from Command Ack
            val initEvtPacket = buildInitEventRequestPacket(sessionId)
            eventOut?.write(initEvtPacket)
            eventOut?.flush()

            // Receive Init_Event_Ack
            val evtAckHeader = readPacketHeader(eventIn!!)
            if (evtAckHeader.packetType != PKT_INIT_EVENT_ACK) {
                Log.e(tag, "Expected Init_Event_Ack (4), got type ${evtAckHeader.packetType}")
                close()
                return@withContext false
            }

            Log.d(tag, "Event connection successfully established with Session ID $sessionId")
            isConnected = true

            // --- STEP 4: Start Background Event Listener Thread ---
            startEventListenerThread()

            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect PTP/IP camera", e)
            close()
            false
        }
    }

    /**
     * Continuously listens for incoming Event packets (Packet Type 8) on the Event socket.
     */
    private fun startEventListenerThread() {
        eventListenerJob = scope.launch {
            Log.d(tag, "Event listener thread started.")
            val stream = eventIn ?: return@launch
            while (isActive && isConnected) {
                try {
                    val header = readPacketHeader(stream)
                    val payloadLength = header.length - 8
                    if (payloadLength < 0) continue

                    val payload = ByteArray(payloadLength)
                    stream.readFully(payload)

                    if (header.packetType == PKT_EVENT) {
                        parseEventPacket(payload)
                    } else {
                        Log.d(tag, "Received non-event packet on Event Socket: Type ${header.packetType}")
                    }
                } catch (e: Exception) {
                    if (isConnected) {
                        Log.e(tag, "Error reading from Event socket", e)
                        _eventFlow.emit(PtpIpEvent.ConnectionError(e.message ?: "Event socket read error"))
                    }
                    break
                }
            }
        }
    }

    /**
     * Parses PTP/IP Event Packet (Type 8).
     * Format:
     * - Event Code (2 bytes, UINT16)
     * - Transaction ID / Sequence ID (4 bytes, UINT32)
     * - Param 1 (4 bytes, UINT32) -> Object Handle
     * - Param 2..5 (4 bytes each)
     */
    private suspend fun parseEventPacket(payload: ByteArray) {
        if (payload.size < 6) return
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val eventCode = buf.short.toInt() and 0xFFFF
        val transId = buf.int
        val param1 = if (buf.remaining() >= 4) buf.int else 0

        Log.d(tag, "PTP Event Received -> Code: 0x${eventCode.toString(16).uppercase()}, Param1: 0x${param1.toString(16).uppercase()}")

        when (eventCode) {
            EVENT_OBJECT_ADDED -> {
                Log.d(tag, "Standard ObjectAdded (0x4002) Event! Object Handle: $param1")
                _eventFlow.emit(PtpIpEvent.ObjectAdded(objectHandle = param1, isSdram = false))
            }
            NIKON_EVENT_OBJECT_ADDED_IN_SDRAM -> {
                Log.d(tag, "Nikon ObjectAddedInSDRAM (0xC101) Event! Object Handle: $param1")
                _eventFlow.emit(PtpIpEvent.ObjectAdded(objectHandle = param1, isSdram = true))
            }
            else -> {
                _eventFlow.emit(PtpIpEvent.UnknownEvent(eventCode, param1))
            }
        }
    }

    /**
     * Sends GetObjectInfo (0x1008) command over Command Socket to retrieve object details (file name, size).
     */
    suspend fun getObjectInfo(objectHandle: Int): ObjectInfo? = withContext(Dispatchers.IO) {
        val out = commandOut ?: return@withContext null
        val input = commandIn ?: return@withContext null

        try {
            val currentTransId = transactionId++
            val reqPacket = buildCommandRequestPacket(
                opcode = OP_GET_OBJECT_INFO,
                transactionId = currentTransId,
                params = intArrayOf(objectHandle)
            )

            out.write(reqPacket)
            out.flush()

            // Read response
            var info: ObjectInfo? = null
            while (true) {
                val header = readPacketHeader(input)
                val payloadLength = header.length - 8
                val payload = ByteArray(payloadLength)
                input.readFully(payload)

                val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                if (header.packetType == 9 || header.packetType == 10 || header.packetType == 12) {
                    // Start Data / Data / End Data phase containing ObjectInfo Dataset
                    if (payloadLength >= 12) {
                        val storageId = buf.int
                        val objFormat = buf.short
                        val objProtection = buf.short
                        val objCompressedSize = buf.int

                        // Extract Filename string if available
                        var fileName = "DSC_${objectHandle}.JPG"
                        try {
                            if (buf.remaining() > 10) {
                                val strLen = buf.get().toInt() and 0xFF
                                if (strLen > 0 && buf.remaining() >= strLen * 2) {
                                    val chars = CharArray(strLen)
                                    for (i in 0 until strLen) {
                                        chars[i] = buf.char
                                    }
                                    fileName = String(chars).trimEnd('\u0000')
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Could not parse filename from ObjectInfo, fallback to default")
                        }

                        info = ObjectInfo(
                            objectHandle = objectHandle,
                            storageId = storageId,
                            objectFormat = objFormat,
                            objectCompressedSize = objCompressedSize,
                            fileName = fileName
                        )
                    }
                } else if (header.packetType == PKT_COMMAND_RESP) {
                    // Command Response Phase
                    val respCode = buf.short.toInt() and 0xFFFF
                    Log.d(tag, "GetObjectInfo Command Response: 0x${respCode.toString(16)}")
                    break
                }
            }
            info
        } catch (e: Exception) {
            Log.e(tag, "GetObjectInfo failed for handle $objectHandle", e)
            null
        }
    }

    /**
     * Sends GetObject (0x1009) command over Command Socket to download the raw JPEG photo bytes.
     */
    suspend fun getObject(objectHandle: Int): ByteArray? = withContext(Dispatchers.IO) {
        val out = commandOut ?: return@withContext null
        val input = commandIn ?: return@withContext null

        try {
            val currentTransId = transactionId++
            val reqPacket = buildCommandRequestPacket(
                opcode = OP_GET_OBJECT,
                transactionId = currentTransId,
                params = intArrayOf(objectHandle)
            )

            out.write(reqPacket)
            out.flush()

            var imageBytes: ByteArray? = null
            val byteStream = java.io.ByteArrayOutputStream()

            while (true) {
                val header = readPacketHeader(input)
                val payloadLength = header.length - 8
                val payload = ByteArray(payloadLength)
                input.readFully(payload)

                when (header.packetType) {
                    9 -> {
                        // Start Data Phase: Payload is TransactionID (4 bytes) + Total File Size (8 bytes).
                        // Skip header payload (no JPEG data)
                        continue
                    }
                    10, 12 -> {
                        // Data and End Data Packets: The first 4 bytes are the TransactionID.
                        // Skip those 4 bytes to get the actual JPEG data bytes.
                        if (payload.size > 4) {
                            byteStream.write(payload, 4, payload.size - 4)
                        }
                    }
                    PKT_COMMAND_RESP -> {
                        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                        val respCode = buf.short.toInt() and 0xFFFF
                        Log.d(tag, "GetObject Command Response: 0x${respCode.toString(16)}")
                        if (respCode == RESP_OK) {
                            imageBytes = byteStream.toByteArray()
                        }
                        break
                    }
                }
            }
            imageBytes
        } catch (e: Exception) {
            Log.e(tag, "GetObject failed for handle $objectHandle", e)
            null
        }
    }

    // --- PACKET BUILDERS ---

    private fun buildInitCommandRequestPacket(guid: UUID, name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_16LE)
        val nameTerminated = ByteArray(nameBytes.size + 2)
        System.arraycopy(nameBytes, 0, nameTerminated, 0, nameBytes.size)

        val totalLen = 4 + 4 + 16 + nameTerminated.size + 4
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(totalLen)
        buf.putInt(PKT_INIT_COMMAND_REQ)

        // 16-byte GUID
        val msb = guid.mostSignificantBits
        val lsb = guid.leastSignificantBits
        val guidBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        guidBuf.putLong(msb)
        guidBuf.putLong(lsb)
        buf.put(guidBuf.array())

        // Friendly Name
        buf.put(nameTerminated)

        // Protocol Version (1.0)
        buf.putInt(0x00010000)

        return buf.array()
    }

    private fun buildInitEventRequestPacket(sessionId: Int): ByteArray {
        val totalLen = 4 + 4 + 4
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(totalLen)
        buf.putInt(PKT_INIT_EVENT_REQ)
        buf.putInt(sessionId)
        return buf.array()
    }

    private fun buildCommandRequestPacket(opcode: Int, transactionId: Int, params: IntArray): ByteArray {
        val totalLen = 4 + 4 + 4 + 2 + 4 + (params.size * 4)
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(totalLen)
        buf.putInt(PKT_COMMAND_REQ)
        buf.putInt(1) // Data phase info: 1 = No data / Data Out, 2 = Data In
        buf.putShort(opcode.toShort())
        buf.putInt(transactionId)
        for (p in params) {
            buf.putInt(p)
        }
        return buf.array()
    }

    private data class PacketHeader(val length: Int, val packetType: Int)

    private fun readPacketHeader(input: DataInputStream): PacketHeader {
        val length = Integer.reverseBytes(input.readInt())
        val packetType = Integer.reverseBytes(input.readInt())
        return PacketHeader(length, packetType)
    }

    fun close() {
        isConnected = false
        eventListenerJob?.cancel()
        try { commandSocket?.close() } catch (_: Exception) {}
        try { eventSocket?.close() } catch (_: Exception) {}
        commandSocket = null
        eventSocket = null
    }
}
