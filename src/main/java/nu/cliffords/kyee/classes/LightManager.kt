package nu.cliffords.kyee.classes

import android.util.Log
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONArray
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URI

/**
 * Created by Henrik Nelson on 2017-08-15.
 */
class LightManager private constructor() {

    private object Holder {
        val INSTANCE = LightManager()
    }

    companion object {
        val instance: LightManager by lazy { Holder.INSTANCE }
    }

    private val lights: MutableMap<String, Light> = mutableMapOf()

    private val MCAST_ADDR = "239.255.255.250"
    private val MCAST_PORT = 1982

    fun getLights(listener: (List<Light>)->Unit, timeoutTime: Int = 4){

        doAsync {

            val socket = MulticastSocket()
            try {
                socket.soTimeout = 1000
                socket.joinGroup(InetAddress.getByName(MCAST_ADDR))

                Log.d("kYee", "LightManager - Sending multicast discovery message")
                sendDiscoveryMessage(socket)

                val responseList = getDiscoveryResponses(socket, timeoutTime)

                val existingIds = mutableListOf<String>()

                //Parse responses to see if we have any new lights
                responseList.forEach { response ->

                    //Make a map of all the interesting key/value pairs in the response
                    val propMap = response.split("\r\n").filter { propertyRow -> propertyRow.contains(":") }.associateBy(
                            { it.split(delimiters = ":", ignoreCase = false, limit = 2)[0] },
                            { it.split(delimiters = ":", ignoreCase = false, limit = 2)[1].trim() }
                    )

                    //If this seems to be a legit response and we aren't already keeping track of this particular light..
                    if (propMap.containsKey("id") && !(lights.containsKey(propMap["id"]))) {
                        val lightAddress = URI.create(propMap.getValue("Location"))
                        val light = Light(lightAddress)
                        light.id = propMap.getValue("id")
                        light.firmware_version = propMap.getValue("fw_ver")
                        light.model = propMap.getValue("model")
                        light.support = propMap.getValue("support").split(" ").toTypedArray()
                        light.power = propMap.getValue("power") == "on"
                        light.brightness = propMap.getValue("bright").toInt()
                        light.color_mode = propMap.getValue("color_mode").toString().toInt()
                        light.ct = propMap.getValue("ct").toInt()
                        light.rgb = propMap.getValue("rgb").toInt()
                        light.hue = propMap.getValue("hue").toInt()
                        light.saturation = propMap.getValue("sat").toInt()
                        light.name = propMap.getValue("name")
                        existingIds.add(light.id)
                        lights.put(light.id, light)
                    } else if (propMap.containsKey("id") && (lights.containsKey(propMap["id"]))) {
                        existingIds.add(propMap.getValue("id"))
                    }
                }

                //Make sure devices that has, but is no longer responding to discovery packages, is removed from our internal list of active devices
                val currentLightIds = lights.keys.toList()
                for(id in currentLightIds){
                    if (!existingIds.contains(id)) {
                        Log.d("kYee", "LightManager: existing YeeLight device with id $id not discovered now, removing from internal light list")
                        lights.remove(id)
                    }
                }

                //DEBUG
                Log.d("kYee", "LightManager - all current lights: ${JSONArray(lights.keys)}")
            }catch(e: Exception)
            {
                Log.e("kYee","Could not discover devices - reason: ${e.message}")
            }
            
            uiThread {
                Log.d("kYee","LightManager - sending all discovered lights to caller")
                listener(lights.values.toList())
            }

        }
    }

    fun getLightFromId(lightId: String): Light? {
        return this.lights[lightId]
    }

    //Send multicast message that triggers a response in Yeelight devices
    private fun sendDiscoveryMessage(socket: MulticastSocket) {
        val msg = "M-SEARCH * HTTP/1.1\r\nST: wifi_bulb\r\nMAN: \"ssdp:discover\"\r\n"
        val sendData = msg.toByteArray(charset("UTF-8"))
        val addr = InetAddress.getByName(MCAST_ADDR)
        val sendPacket = DatagramPacket(sendData, sendData.size, addr, MCAST_PORT)
        Log.d("kYee","LightManager - Sending multicast discovery message: $msg")
        socket.send(sendPacket)
    }

    //Within the specified timeout period, try to receive as many device responses as possible from the local network
    //Returns a list with the string encoded responses
    private fun getDiscoveryResponses(socket: MulticastSocket,timeout: Int) : List<String>{
        val responseList: MutableList<String> = mutableListOf()
        val startTime = System.currentTimeMillis()

        while (true){
            try{

                //Receive responses from all lights in the network
                val buffer = ByteArray(1024)
                val datagram = DatagramPacket(buffer, buffer.size)
                socket.receive (datagram)

                //Store away the string representations of the lights
                val responseString = String(datagram.data, 0, datagram.length)
                if(!responseList.contains(responseString)) {
                    Log.d("kYee","Received multicast response: $responseString")
                    responseList.add(responseString)
                }

            }catch(e: Exception) {
                val endTime = System.currentTimeMillis()
                if ((endTime - startTime) > timeout * 1000)
                    break
            }
        }
        return responseList
    }

}