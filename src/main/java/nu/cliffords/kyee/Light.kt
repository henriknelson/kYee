package nu.cliffords.kyee

import android.os.Handler
import android.util.Log
import com.github.kittinunf.fuel.core.FuelManager
import org.json.JSONObject
import java.net.*
import android.os.Looper
import android.util.Base64
import nu.cliffords.kyee.teeceepee.TCPClient
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.nio.charset.Charset


/**
 * Created by Henrik Nelson on 2017-08-14.
 */
class Light {

    var name: String = ""
    var uri: URI? = null
    var location: String = ""
    var id: String = ""
    var model: String = ""
    var firmware_version: String = ""
    var support: Array<String> = emptyArray()
    var power: Boolean = false
    var brightness: Int = 0

    enum class ColorMode(val mode: Int) {
        COLOR(1),
        COLOR_TEMP(2),
        HSV(3)
    }

    enum class LightEffect(val value: String)
    {
        SUDDEN("sudden"),
        SMOOTH("smooth")
    }

    companion object Factory {

        fun getLights(listener: (List<Light>) -> Unit, timeout: Int = 2) {
            val socket = MulticastSocket() // must bind receive side
            sendDiscovery(socket)
            var lightList: MutableList<Light> = mutableListOf()
            receiveDiscoveredDevices(socket,{ lightMap ->
                lightMap.forEach { _,value ->
                    Log.d("kYee",value.toString())
                    val light = Light()
                    light.name = Base64.decode(value.getString("name"),Base64.DEFAULT).toString(Charset.forName("UTF-8"))
                    light.id = value.getString("id")
                    light.location = value.getString("Location")
                    light.uri = URI.create(light.location)
                    light.firmware_version =  value.getString("fw_ver")
                    light.model = value.getString("model")
                    light.support = value.getString("support").split(" ").toTypedArray()
                    light.power = value.getString("power") == "on"
                    light.brightness = value.getString("bright").toInt()
                    lightList.add(light)
                }
                listener(lightList)
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
        private fun receiveDiscoveredDevices(socket:MulticastSocket, listener: (Map<String,JSONObject>) -> Unit, timeout: Int = 2 ) {
            doAsync {
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
                uiThread {
                    listener(returnMap)
                }

            }
        }

    }


    private fun getProp(propName: String, listener: (String) -> Unit) {
        TCPClient.send(this.uri!!,"get_prop",arrayListOf<String>(propName),
                { jsonResponse ->
                    listener(jsonResponse.getJSONArray("result").getString(0))
                },{ errorMessage ->
            Log.e("kYee","Could not get property - reason: $errorMessage")
        })
    }

    fun setPower(state: Boolean, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        var stateString = if (state) "on" else "off"
        val params = arrayListOf<Any>(stateString,effect.value,duration)
        TCPClient.send(this.uri!!,"set_power",params,
        { jsonResponse ->
            this.power = state
            listener(jsonResponse)
        },{ errorMessage ->
            Log.e("kYee","Could not set power - reason: $errorMessage")
        })
    }

    fun getPower(listener: (Boolean) -> Unit){
        this.getProp("power",{ stringValue ->
            listener(stringValue.equals("on"))
        })
    }

    fun setBrightness(brightness: Int, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        val params = arrayListOf<Any>(if(brightness <= 0) 1 else if(brightness > 100) 100 else brightness,effect.value,duration)
        TCPClient.send(this.uri!!,"set_bright",params,
                { jsonResponse ->
                    this.brightness = brightness
                    listener(jsonResponse)
                },{ errorMessage ->
            Log.e("kYee","Could not set brightness - reason: $errorMessage")
        })
    }

    fun getBrightness(listener: (Int) -> Unit){
        this.getProp("bright",{ stringValue ->
            listener(stringValue.toInt())
        })
    }

    fun setName(name: String, listener: (JSONObject) -> Unit) {
        //val encodedName = Base64.encodeToString(name.toByteArray(Charset.forName("UTF-8")),Base64.DEFAULT);
        val params = arrayListOf<Any>(name)
        TCPClient.send(this.uri!!,"set_name",params,
                { jsonResponse ->
                    this.name = name
                    listener(jsonResponse)
                },{ errorMessage ->
            Log.e("kYee","Could not set name - reason: $errorMessage")
        })
    }

    fun getName(listener: (String) -> Unit){
        this.getProp("name",{ stringValue ->
            listener(stringValue)
        })
    }
}