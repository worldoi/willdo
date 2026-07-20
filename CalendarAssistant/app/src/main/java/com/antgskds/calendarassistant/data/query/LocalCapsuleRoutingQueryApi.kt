package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.CapsuleRouteMode
import com.antgskds.calendarassistant.core.query.CapsuleRoutingQueryApi

class LocalCapsuleRoutingQueryApi : CapsuleRoutingQueryApi {
    override fun resolveMode(liveCapsuleEnabled: Boolean): CapsuleRouteMode {
        if (!liveCapsuleEnabled) return CapsuleRouteMode.STANDARD_NOTIFICATION
        return CapsuleRouteMode.LIVE_CAPSULE
    }
}
