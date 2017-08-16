package nu.cliffords.kyee.teeceepee

import android.util.Log
import nu.cliffords.kyee.interfaces.LightStateChangeListener
import org.jetbrains.anko.uiThread
import org.json.JSONObject
import java.net.Socket
import org.jetbrains.anko.doAsync
import org.json.JSONArray
import java.io.*
import java.net.URI

/**
 * Created by henrik.nelson2 on 2017-08-15.
 */
class TCPClient(val uri: URI, val stateChangeListener: LightStateChangeListener) {

    init {
        listenToLightCommunication()
    }

    var mId: Int = 1

    fun getId(): Int {
        val returnId = this.mId
        this.mId++
        if (this.mId == 255)
            this.mId = 1
        return returnId
    }

    fun listenToLightCommunication(){

        doAsync {

            val socket = Socket(uri.host, uri.port)
            val inFromServer = BufferedReader(InputStreamReader(socket.getInputStream()))
            socket.soTimeout = 60000

            while(true) {
                try {
                    val responseString = inFromServer.readLine()
                    val responseJson = JSONObject(responseString)
                    if(responseJson.getString("method").equals("props")){
                        val params = responseJson.getJSONObject("params")
                        val paramMap: MutableMap<String, Any> = linkedMapOf()
                        for (key in params.keys()) {
                            paramMap.put(key,params.get(key))
                        }
                        uiThread {
                            stateChangeListener.onStateChanged(paramMap)
                        }
                    }
                }catch(e:Exception) {
                    //Nuffin'
                }

            }
        }
    }



    fun send(method: String, params: List<Any>, successListener: (JSONObject) -> Unit, errorListener: (String?) -> Unit){

        val requestString = JSONObject("{\"id\":${getId()},\"method\":${method},\"params\":${JSONArray(params).toString()}}").toString()

        doAsync {

            val socket = Socket(uri.host,uri.port)

            socket.soTimeout = 2000
            try {
                val outToServer = DataOutputStream(socket.getOutputStream())
                val inFromServer = BufferedReader(InputStreamReader(socket.getInputStream()))
                outToServer.writeBytes(requestString+"\r\n")
                Log.i("kYee","-> $requestString")
                val responseString = inFromServer.readLine()
                Log.i("kYee","<- $responseString")
                val responseJson = JSONObject(responseString)
                uiThread {
                    successListener(responseJson)
                }
            }catch(e: Exception){
                uiThread {
                    errorListener(e.message)
                }
            }

            socket.close()

        }
    }

}