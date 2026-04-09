package com.kmpfacelink

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

/**
 * React Native package registration for the FaceLink Turbo Module.
 *
 * This class is auto-discovered by React Native's new architecture module system.
 * It registers [FaceLinkModule] as a Turbo Module.
 */
class FaceLinkPackage : BaseReactPackage() {

    override fun getModule(
        name: String,
        reactContext: ReactApplicationContext,
    ): NativeModule? {
        return if (name == FaceLinkModule.NAME) {
            FaceLinkModule(reactContext)
        } else {
            null
        }
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            mapOf(
                FaceLinkModule.NAME to ReactModuleInfo(
                    FaceLinkModule.NAME,
                    FaceLinkModule.NAME,
                    false, // canOverrideExistingModule
                    false, // needsEagerInit
                    false, // isCxxModule
                    true,  // isTurboModule
                )
            )
        }
    }
}
