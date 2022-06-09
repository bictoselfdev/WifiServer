package com.example.wifiserver.net

import com.example.wifiserver.net.WifiConstant.PORT_NUM
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.*
import android.util.Log

class WifiServer {

    private var acceptThread: AcceptThread? = null
    private var commThread: CommThread? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var socketListener: SocketListener? = null

    fun setOnSocketListener(listener: SocketListener?) {
        socketListener = listener
    }

    fun onConnect() {
        socketListener?.onConnect()
    }

    fun onDisconnect() {
        socketListener?.onDisconnect()
    }

    fun onLogPrint(message: String?) {
        socketListener?.onLogPrint(message)
    }

    fun onError(e: Exception) {
        socketListener?.onError(e)
    }

    fun onReceive(msg: String) {
        socketListener?.onReceive(msg)
    }

    fun onSend(msg: String) {
        socketListener?.onSend(msg)
    }

    fun accept() {
        stop()

        acceptThread = AcceptThread()
        Timer().schedule(object : TimerTask() {
            override fun run() {
                onLogPrint("Waiting for accept the client..\n")
                acceptThread?.start()

                onLogPrint("[Host IP information]\n" + getHostIpinformation())
            }
        }, 500)
    }

    fun stop() {
        if (acceptThread == null) return

        try {
            acceptThread?.let {
                onLogPrint("Stop accepting")

                it.stopThread()
                it.join(500)
                it.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getHostIpinformation(): String {
        var ip = ""
        try {
            val enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (enumNetworkInterfaces.hasMoreElements()) {
                val networkInterface = enumNetworkInterfaces.nextElement()
                val enumInetAddress = networkInterface.inetAddresses
                while (enumInetAddress.hasMoreElements()) {
                    val inetAddress = enumInetAddress.nextElement()
                    val hostAddress = inetAddress.hostAddress
                    if (hostAddress.contains("192.168.")) { // Class C IP 주소만 취급 (Private Address)
                        ip += "$hostAddress\n"
                    }
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        when (ip) {
            "" -> return "No IP information."
            else -> return ip
        }
    }

    inner class AcceptThread : Thread() {
        private var acceptSocket: ServerSocket? = null
        private var socket: Socket? = null

        override fun run() {
            while (true) {
                socket = try {
                    acceptSocket?.accept()
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }

                if (socket != null) {
                    onConnect()

                    commThread = CommThread(socket)
                    commThread?.start()
                    break
                }
            }
        }

        fun stopThread() {
            try {
                acceptSocket?.close()
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        init {
            try {
                acceptSocket = ServerSocket(PORT_NUM)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal inner class CommThread(private val socket: Socket?): Thread() {

        override fun run() {
            try {
                outputStream = socket?.outputStream
                inputStream = socket?.inputStream
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var len: Int
            val buffer = ByteArray(1024)
            val byteArrayOutputStream = ByteArrayOutputStream()

            while (true) {
                try {
                    len = socket?.inputStream?.read(buffer)!!
                    val data = buffer.copyOf(len)
                    byteArrayOutputStream.write(data)

                    socket.inputStream?.available()?.let { available ->

                        if (available == 0) {
                            val dataByteArray = byteArrayOutputStream.toByteArray()
                            val dataString = String(dataByteArray)
                            onReceive(dataString)

                            byteArrayOutputStream.reset()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopThread()
                    accept()
                    break
                }
            }
        }

        fun stopThread() {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendData(msg: String) {
        if (outputStream == null) return

        object : Thread() {
            override fun run() {
                try {
                    outputStream?.let {
                        onSend(msg)

                        it.write(msg.toByteArray())
                        it.flush()
                    }
                } catch (e: Exception) {
                    onError(e)
                    e.printStackTrace()
                    stop()
                }
            }
        }.start()
    }
}