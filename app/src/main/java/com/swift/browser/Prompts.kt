package com.swift.browser

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate

/** Site permissions: location, camera, microphone, notifications, DRM. */
class PermissionsImpl(private val b: BrowserState) : PermissionDelegate {

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: PermissionDelegate.Callback,
    ) {
        val host = b.host
        if (host == null || permissions == null) { callback.reject(); return }
        host.requestAndroidPermissions(arrayOf(*permissions)) { granted -> if (granted) callback.grant() else callback.reject() }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: PermissionDelegate.ContentPermission,
    ): GeckoResult<Int>? {
        val what = when (perm.permission) {
            PermissionDelegate.PERMISSION_GEOLOCATION -> "know your location"
            PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "send you notifications"
            PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "store data on this device"
            PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "play protected (DRM) content"
            else -> return GeckoResult.fromValue(PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        val result = GeckoResult<Int>()
        b.prompt = PromptRequest(
            PromptKind.CONFIRM, hostOf(perm.uri), "wants to $what", positive = "Allow", negative = "Block",
        ) { r ->
            result.complete(
                if (r?.ok == true) PermissionDelegate.ContentPermission.VALUE_ALLOW
                else PermissionDelegate.ContentPermission.VALUE_DENY
            )
        }
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out PermissionDelegate.MediaSource>?,
        audio: Array<out PermissionDelegate.MediaSource>?,
        callback: PermissionDelegate.MediaCallback,
    ) {
        val what = listOfNotNull(if (video?.isNotEmpty() == true) "camera" else null, if (audio?.isNotEmpty() == true) "microphone" else null)
            .joinToString(" and ")
        b.prompt = PromptRequest(
            PromptKind.CONFIRM, hostOf(uri), "wants to use your $what", positive = "Allow", negative = "Block",
        ) { r ->
            if (r?.ok == true) callback.grant(video?.firstOrNull(), audio?.firstOrNull()) else callback.reject()
        }
    }
}

/** JavaScript dialogs, <select> lists and file upload. Date/colour pickers and HTTP auth are not implemented yet. */
class PromptsImpl(private val b: BrowserState) : PromptDelegate {

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val r = GeckoResult<PromptDelegate.PromptResponse>()
        b.prompt = PromptRequest(PromptKind.ALERT, prompt.title ?: "", prompt.message) { r.complete(prompt.dismiss()) }
        return r
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val r = GeckoResult<PromptDelegate.PromptResponse>()
        b.prompt = PromptRequest(PromptKind.CONFIRM, prompt.title ?: "", prompt.message, positive = "OK", negative = "Cancel") { res ->
            r.complete(
                if (res?.ok == true) prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)
                else prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)
            )
        }
        return r
    }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val r = GeckoResult<PromptDelegate.PromptResponse>()
        b.prompt = PromptRequest(
            PromptKind.TEXT, prompt.title ?: "", prompt.message, text = prompt.defaultValue ?: "", positive = "OK", negative = "Cancel",
        ) { res -> r.complete(if (res?.ok == true) prompt.confirm(res.text) else prompt.dismiss()) }
        return r
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val r = GeckoResult<PromptDelegate.PromptResponse>()
        val items = ArrayList<ChoiceItem>()
        fun flat(arr: Array<out PromptDelegate.ChoicePrompt.Choice>?) {
            arr?.forEach { c ->
                val kids = c.items
                if (kids != null && kids.isNotEmpty()) {
                    items.add(ChoiceItem(c.id, c.label ?: "", false, false, header = true))
                    flat(kids)
                } else if (!c.separator) {
                    items.add(ChoiceItem(c.id, c.label ?: "", !c.disabled, c.selected))
                }
            }
        }
        flat(prompt.choices)
        val multi = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
        b.prompt = PromptRequest(
            PromptKind.CHOICE, prompt.title ?: "", prompt.message, choices = items, multi = multi, positive = "OK", negative = "Cancel",
        ) { res ->
            r.complete(
                when {
                    res == null || !res.ok -> prompt.dismiss()
                    multi -> prompt.confirm(res.ids.toTypedArray())
                    res.ids.isNotEmpty() -> prompt.confirm(res.ids[0])
                    else -> prompt.dismiss()
                }
            )
        }
        return r
    }

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptDelegate.PromptResponse>? {
        val host = b.host ?: return GeckoResult.fromValue(prompt.dismiss())
        val r = GeckoResult<PromptDelegate.PromptResponse>()
        val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
        host.pickFiles(multiple, prompt.mimeTypes) { uris ->
            val ctx = host as android.content.Context
            r.complete(
                when {
                    uris.isEmpty() -> prompt.dismiss()
                    multiple -> prompt.confirm(ctx, uris.toTypedArray())
                    else -> prompt.confirm(ctx, uris[0])
                }
            )
        }
        return r
    }
}
