package nu.cliffords.kyee.classes

/**
 * Created by Henrik Nelson on 2017-08-20.
 */

class Helpers {

    companion object {
        fun getIntFromColor(Red: Int, Green: Int, Blue: Int): Int {
            val r = Red shl 16 and 0x00FF0000 //Shift red 16-bits and mask out other stuff
            val g = Green shl 8 and 0x0000FF00 //Shift Green 8-bits and mask out other stuff
            val b = Blue and 0x000000FF //Mask out anything not blue.
            return 0x000000 or r or g or b //0xFF000000 for 100% Alpha. Bitwise OR everything together.
        }
    }

}