package nu.cliffords.kyee.network

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
 * Created by Henrik Nelson on 2017-08-15.
 */

//Manages network communication with a Yeelight device
//Listens for unsolicited communication from the device, and facilitates the sending and receiving of requests and responses to and from the device

class TCPClient(val uri: URI,val stateChangeListener: LightStateChangeListener) {

    init {
        listenToDeviceNotifications()
    }


    var mTransactionId: Int = 1

    fun getTransactionId(): Int {
        val returnId = this.mTransactionId
        this.mTransactionId++
        if (this.mTransactionId == 255)
            this.mTransactionId = 1
        return returnId
    }

    //Itirates forever in a thread, listening for unsolicited communications from the device
    fun listenToDeviceNotifications(){

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

        val requestString = JSONObject("{\"id\":${getTransactionId()},\"method\":${method},\"params\":${JSONArray(params).toString()}}").toString()

        doAsync {

            val socket = Socket(uri.host,uri.port)

            socket.soTimeout = 2000
            try {

                val outToServer = DataOutputStream(socket.getOutputStream())
                val inFromServer = BufferedReader(InputStreamReader(socket.getInputStream()))

                outToServer.writeBytes(requestString+"\r\n")
                Log.i("kYee","Light<${uri.host}:${uri.port}> -> $requestString")
                val responseString = inFromServer.readLine()
                Log.i("kYee","Light<${uri.host}:${uri.port}> <- $responseString")

                val responseJson = JSONObject(responseString)

                if(responseJson.has("result")){
                    uiThread {
                        successListener(responseJson)
                    }
                }else if(responseJson.has("error"))
                {
                    val errorMessage = responseJson.getJSONObject("error").getString("message")
                    throw Exception(errorMessage)
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