package nu.cliffords.kyee.classes

import android.util.Log
import nu.cliffords.kyee.classes.Flow.FlowState
import nu.cliffords.kyee.interfaces.LightStateChangeListener
import nu.cliffords.kyee.network.TCPClient
import org.json.JSONObject
import java.net.URI

/**
 * Created by Henrik Nelson on 2017-08-14.
 */

//This class represents a single Yeelight smart device
//Can not be instantiated by anyone except module members (e.g. LightManager)

class Light internal constructor(lightAddress: URI): LightStateChangeListener {

    private var client: TCPClient? = null
    private var listeners: MutableList<LightStateChangeListener> = mutableListOf()

    var id: String = ""
    var model: String = ""
    var firmware_version: String = ""
    var support: Array<String> = emptyArray()
    var power: Boolean = false
    var brightness: Int = 100
    var color_mode: Int = 1
    var ct: Int = 4000
    var rgb: Int = 0
    var hue: Int = 100
    var saturation: Int = 100
    var name: String = ""
    var flowing: Boolean = false
    var flow_parameters: Array<String> = emptyArray()

    enum class ColorMode(val value: Int) {
        COLOR(1),
        COLOR_TEMP(2),
        HSV(3)
    }

    enum class LightEffect(val value: String)
    {
        SUDDEN("sudden"),
        SMOOTH("smooth")
    }

    enum class FlowAction(val value: Int)
    {
        LED_RECOVERY(0),    //0 means smart LED recover to the state before the color flow started.
        LED_STAY(1),        //1 means smart LED stay at the state when the flow is stopped.
        LED_TURNOFF(2)      //2 means turn off the smart LED after the flow is stopped.
    }

    init {
        client = TCPClient(lightAddress,this)
    }

    fun registerStateListener(listener: LightStateChangeListener) {
        if(!listeners.contains(listener))
            listeners.add(listener)
    }

    fun getProperties(properties: Array<String>, listener: (JSONObject) -> Unit) {
        val params = properties.toList()
        client!!.send("get_prop",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not get properties - reason: $errorMessage")
                })
    }

    fun setColorTemperature(colorTemperature: Int, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        val params = arrayListOf(if(colorTemperature < 1700) 1700 else if(colorTemperature > 6500) 6500 else colorTemperature,effect.value,duration)
        client!!.send("set_ct_abx",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set color temperature - reason: $errorMessage")
                })
    }

    fun setRGB(color: Int, effect: LightEffect, duration: Int, listener: (JSONObject) -> Unit) {
        val params = arrayListOf(if(color < 0) 0 else if(color > 0xFFFFFF) 0xFFFFFF else color,effect.value,duration)
        client!!.send("set_rgb",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set RGB - reason: $errorMessage")
                })
    }

    fun setRGB(red: Int, green: Int, blue:Int, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        val colorRgb = Helpers.getIntFromColor(red,green,blue)
        setRGB(colorRgb,effect,duration,listener)
    }

    fun setHSV(hue: Int, saturation: Int, effect: LightEffect, duration: Int, listener: (JSONObject) -> Unit) {
        val safeHue = if(hue < 0) 0 else if(hue > 359) 359 else hue
        val safeSat = if(saturation < 0) 0 else if(saturation > 100) 100 else saturation
        val params = arrayListOf(safeHue,safeSat,effect.value,duration)
        client!!.send("set_hsv",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set HSV - reason: $errorMessage")
                })
    }

    fun setBrightness(brightness: Int, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        val params = arrayListOf(if(brightness <= 0) 1 else if(brightness > 100) 100 else brightness,effect.value,duration)
        client!!.send("set_bright",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not set brightness - reason: $errorMessage")
                })
    }

    fun setPower(state: Boolean, effect: LightEffect, duration:Int, listener: (JSONObject) -> Unit) {
        val stateString = if (state) "on" else "off"
        val params = arrayListOf(stateString,effect.value,duration)
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

    fun startColorFlow(count: Int, action: FlowAction, states:List<FlowState>, listener: (JSONObject) -> Unit) {

        val params = arrayListOf(count,action.value,states.joinToString(","))
        client!!.send("start_cf",params,
                { jsonResponse ->
                    listener(jsonResponse)
                },
                { errorMessage ->
                    Log.e("kYee","Could not start color flow - reason: $errorMessage")
                })
    }

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
        if (params.containsKey("flowing"))
            this.flowing = params.getValue("flowing").toString() == "1"

        listeners.forEach { listener ->
            listener.onStateChanged(params)
        }
    }

}