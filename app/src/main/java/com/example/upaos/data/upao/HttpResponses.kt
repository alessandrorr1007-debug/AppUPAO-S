package com.example.upaos.data.upao

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** The response is always closed, including non-2xx, parsing failure and cancellation. */
internal fun <T> consumeHttpResponse(client: OkHttpClient, request: Request, consume: (Response) -> T): T =
    client.newCall(request).execute().use(consume)
