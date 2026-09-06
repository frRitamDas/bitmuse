package com.music.bitchord

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.selectChannel as applyChannel

/** Makes the restored application bootstrap call resolve without importing an extension explicitly. */
fun Innertube.selectChannel(pageId: String?, dataSyncId: String?, authUser: String? = null) =
    applyChannel(this, pageId, dataSyncId, authUser)
