package nu.cliffords.kyee

import android.os.Handler
import android.system.Os
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.android.extension.responseJson
import com.github.kittinunf.fuel.core.FuelManager
import android.system.Os.socket
import android.system.Os.socket
import java.io.IOException
import java.lang.reflect.Array.getLength
import android.system.Os.socket
import org.json.JSONObject
import java.net.*
import android.os.Looper




/**
 * Created by Henrik Nelson on 2017-08-14.
 */
class Light {

    var location: String = ""
    var id: String = ""
    var model: String = ""
    var firmware_version: String = ""
    var support: Array<String> = emptyArray()
    var power: Boolean = false


    init {
        //FuelManager.instance.basePath = "http://$ipAddress:55443"
        FuelManager.instance.baseHeaders = mapOf("Content-Type" to "application/json")
        FuelManager.instance.addRequestInterceptor { request ->
            Log.d("kYee",request.toString())
            request
        }
        FuelManager.instance.addResponseInterceptor { response ->
            Log.d("kYee",response.toString())
            response
        }
    }

    companion object Factory {

        fun getLights(listener: (List<Light>) -> Unit, timeout: Int = 4) {
            val socket = MulticastSocket() // must bind receive side
            sendDiscovery(socket)
            var lightList: MutableList<Light> = mutableListOf()
            receiveDiscoveredDevices(socket,{ lightMap ->
                lightMap.forEach { _,value ->
                    Log.d("kYee",value.toString())
                    val light = Light()
                    light.id = value.getString("id")
                    light.location = value.getString("Location").split("yeelight://")[1]
                    light.firmware_version = value.getString("fw_ver")
                    light.model = value.getString("model")
                    light.support = value.getString("support").split(" ").toTypedArray()
                    light.power = value.getString("power") == "on"
                    lightList.add(light)
                }
                Handler(Looper.getMainLooper()).post(Runnable {listener(lightList)  })
            },timeout)

        }

        //Send multicast request
        private fun sendDiscovery(socket:MulticastSocket) {
            val MCAST_ADDR = "239.255.255.250"
            val MCAST_PORT = 1982
            socket.joinGroup(InetAddress.getByName(MCAST_ADDR))
            val msg = "M-SEARCH * HTTP/1.1\r\nST: wifi_bulb\r\nMAN: \"ssdp:discover\"\r\n"
            val sendData = msg.toByteArray(charset("UTF-8"))
            val addr = InetAddress.getByName(MCAST_ADDR)
            val sendPacket = DatagramPacket(sendData, sendData.size, addr, MCAST_PORT)
            socket.send(sendPacket)
        }

        //Try to receive multicast responses
        private fun receiveDiscoveredDevices(socket:MulticastSocket, listener: (Map<String,JSONObject>) -> Unit, timeout: Int = 4 ) {
            Thread(Runnable {
                val returnMap: MutableMap<String,JSONObject> = mutableMapOf()
                val startTime = System.currentTimeMillis()
                socket.soTimeout = 1000
                while(true) {
                    var buffer = ByteArray(1024)
                    var dgram = DatagramPacket(buffer, buffer.size)

                    try {
                        socket.receive(dgram)
                        if(dgram.length > 0) {
                            var lightString = String(dgram.getData(), 0, dgram.getLength())
                            var propMap = lightString.split("\r\n").filter {
                                value -> value.contains(":")
                            }.associateBy(
                                    {it.split(delimiters = ":", ignoreCase =  false, limit = 2)[0]},
                                    {it.split(delimiters = ":", ignoreCase =  false, limit = 2)[1].trim()}
                            )

                            val json = JSONObject(propMap)
                            returnMap.put(propMap.getOrDefault("id","unknown_id"),json)

                        }
                    }catch(e: SocketTimeoutException)
                    {
                        var endTime = System.currentTimeMillis()
                        var timeSinceStart = endTime - startTime
                        if(timeSinceStart > timeout * 1000) {
                            break
                        }
                    }

                }
                listener(returnMap)
            }).start()
        }

    }

    fun setPower(on: Boolean, listener: (String)->Unit){
        var state = ""
        if(on)
            state = "on"
        else
            state = "off"
        Fuel.post("http://$location").body("{ \"id\": 1, \"method\": \"set_power\", \"params\":[\"on\", \"smooth\", 500]}").responseJson { request, response, result ->
            val (json,error) = result
            if(json != null)
                listener(json.toString())
        }
    }
}