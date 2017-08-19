package nu.cliffords.kyee.classes

/**
 * Created by Henrik Nelson on 2017-08-19.
 */

class Flow(val count: Int, val action: Light.FlowAction, val flowStates: List<FlowState>) {

    data class FlowState(val duration: Int, val mode:FlowStateMode,val value: Int,val brightness:Int ) {

        enum class FlowStateMode(val value: Int) {
            COLOR(1),
            COLOR_TEMPERATURE(2),
            SLEEP(7)
        }

        override fun toString(): String {
            return "$duration,${mode.value},$value,$brightness"
        }
    }
}