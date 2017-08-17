package nu.cliffords.kyee.classes

import android.util.Log
import org.json.JSONObject
import java.net.*
import nu.cliffords.kyee.interfaces.LightStateChangeListener
import nu.cliffords.kyee.network.TCPClient


/**
 * Created by Henrik Nelson on 2017-08-14.
 */

//This class represents a single Yeelight smart device
//Can not be instantiated by anyone except module members

class Light internal constructor(val lightAddress: URI): LightStateChangeListener {

    private var client: TCPClient? = null
    private var listeners: MutableList<LightStateChangeListener> = mutableListOf()

    var id: String? = null
    var model: String? = null
    var firmware_version: String? = null
    var support: Array<String>? = null
    var power: Boolean? = null
    var brightness: Int? = null
    var color_mode: Int? = null
    var ct: Int? = null
    var rgb: Int? = null
    var hue: Int? = null
    var saturation: Int? = null
    var name: String? = null

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

    init {
        client = TCPClient(lightAddress,this)
    }

    fun registerStateListener(listener: LightStateChangeListener) {
        listeners.add(listener)
    }

    fun getProperties(properties: Array<String>, listener: (JSONObject) -> Unit) {
        val params = emptyList<String>()
        client!!.send("get_prop",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not get properties - reason: $errorMessage")
                })
    }

    fun setColorTemperature(colorTemperature: Int, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        val params = arrayListOf<Any>(if(colorTemperature < 1700) 1700 else if(colorTemperature > 6500) 6500 else colorTemperature,effect.value,duration)
        client!!.send("set_ct_abx",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set color temperature - reason: $errorMessage")
                })
    }

    fun setRGB(color: Int, effect: LightEffect, duration: Int, listener: (JSONObject) -> Unit) {
        val params = arrayListOf<Any>(if(color < 0) 0 else if(color > 0xFFFFFF) 0xFFFFFF else color,effect.value,duration)
        client!!.send("set_rgb",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set RGB - reason: $errorMessage")
                })
    }

    fun setHSV(hue: Int, saturation: Int, effect: LightEffect, duration: Int, listener: (JSONObject) -> Unit) {
        val safeHue = if(hue < 0) 0 else if(hue > 359) 359 else hue
        val safeSat = if(saturation < 0) 0 else if(saturation > 100) 100 else saturation
        val params = arrayListOf<Any>(safeHue,safeSat,effect.value,duration)
        client!!.send("set_hsv",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set HSV - reason: $errorMessage")
                })
    }

    fun setBrightness(brightness: Int, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        val params = arrayListOf<Any>(if(brightness <= 0) 1 else if(brightness > 100) 100 else brightness,effect.value,duration)
        client!!.send("set_bright",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set brightness - reason: $errorMessage")
                })
    }

    fun setPower(state: Boolean, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        var stateString = if (state) "on" else "off"
        val params = arrayListOf<Any>(stateString,effect.value,duration)
        client!!.send("set_power",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set power - reason: $errorMessage")
                })
    }

    fun toggle(listener: (JSONObject) -> Unit) {
        val params = emptyList<String>()
        client!!.send("toggle",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not toggle power - reason: $errorMessage")
                })
    }

    fun setDefault(listener: (JSONObject) -> Unit) {
        val params = emptyList<String>()
        client!!.send("set_default",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set default - reason: $errorMessage")
                })
    }





    /*fun setColor(red: Int, green: Int, blue:Int, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        var rgb = red
        rgb = (rgb shl 8) + green
        rgb = (rgb shl 8) + blue
        setColor(rgb,effect,duration,listener)
    }*/



    fun setName(name: String, listener: (JSONObject) -> Unit) {
        //val encodedName = Base64.encodeToString(name.toByteArray(Charset.forName("UTF-8")),Base64.DEFAULT);
        val params = arrayListOf<Any>(name)
        client!!.send("set_name",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set name - reason: $errorMessage")
                })
    }

    override fun onStateChanged(params: Map<String,Any>) {
        params.forEach { paramName, paramValue ->
            Log.i("kYee","Param with name \"$paramName\" has changed to \"$paramValue\"")
        }
        if (params.containsKey("model"))
            this.model = params.getValue("model").toString()
        if (params.containsKey("fw_ver"))
            this.firmware_version = params.getValue("fw_ver").toString()
        if (params.containsKey("support"))
            this.support = params.getValue("support").toString().split(" ").toTypedArray()
        if (params.containsKey("power"))
            this.power = params.getValue("power") == "on"
        if (params.containsKey("bright"))
            this.brightness = params.getValue("bright").toString().toInt()
        if (params.containsKey("color_mode"))
            this.color_mode = params.getValue("color_mode").toString().toInt()
        if (params.containsKey("ct"))
            this.ct = params.getValue("ct").toString().toInt()
        if (params.containsKey("rgb"))
            this.rgb = params.getValue("rgb").toString().toInt()
        if (params.containsKey("hue"))
            this.hue = params.getValue("hue").toString().toInt()
        if (params.containsKey("sat"))
            this.saturation = params.getValue("sat").toString().toInt()
        if (params.containsKey("name"))
            this.name = params.getValue("name").toString()
        listeners.forEach { listener ->
            if(listener != null)
                listener.onStateChanged(params)
        }
    }


    /*
    private fun getProp(propName: String, listener: (String) -> Unit) {
        TCPClient.send(this.uri!!,"get_prop",arrayListOf<String>(propName),
                { jsonResponse ->
                    listener(jsonResponse.getJSONArray("result").getString(0))
                },{ errorMessage ->
            Log.e("kYee","Could not get property - reason: $errorMessage")
        })
    }



    fun getPower(listener: (Boolean) -> Unit){
        this.getProp("power",{ stringValue ->
            listener(stringValue.equals("on"))
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
    }*/
}