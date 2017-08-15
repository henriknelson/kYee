package nu.cliffords.kyee.teeceepee

import android.util.Log
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
class TCPClient {

    companion object {

        var mId: Int = 1

        fun getId(): Int {
            synchronized(this.mId,{
                val returnId = this.mId
                this.mId++
                if(this.mId == 255)
                    this.mId = 1
                return returnId
            })
        }

        fun send(uri: URI, method: String, params: List<Any>, successListener: (JSONObject) -> Unit, errorListener: (String?) -> Unit){

            doAsync {
                val socket = Socket(uri.host,uri.port)
                val requestString = JSONObject("{\"id\":${getId()},\"method\":${method},\"params\":${JSONArray(params).toString()}}").toString()

                socket.soTimeout = 1000
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

}