package com.example.demofido2

import android.util.Base64
import java.io.Serializable
import java.nio.ByteBuffer

class Helper {
    companion object {
        @JvmStatic
        fun coerceToArrayBuffer(thing: Any, name: String, base64Flag: Int): Any {
            if (thing is String) {
                // base64url to base64
                var base64 = thing.replace("-", "+").replace("_", "/")

                // base64 to byte array
                val bytes = Base64.decode(base64, base64Flag)
                return bytes
            }

            // Array to byte array
            if (thing is Array<*>) {
                if (thing.isArrayOf<Byte>()) {
                    return (thing as Array<*>).map { it as Byte }.toByteArray()
                } else {
                    throw IllegalArgumentException("Array must be of type ByteArray")
                }
            }

            // byte array to ByteBuffer
            if (thing is ByteArray) {
                return ByteBuffer.wrap(thing)
            }

            // error if none of the above worked
            throw Exception("Could not coerce '$name' to ByteBuffer")
        }

        @JvmStatic
        fun coerceToBase64Url(thing: Any, base64Flag: Int): Serializable {
            // Array or ByteBuffer to byte array
            if (thing is Array<*>) {
                if (thing.isArrayOf<Byte>()) {
                    thing as Array<*>
                    return thing.map { it as Byte }.toByteArray()
                } else {
                    throw IllegalArgumentException("Array must be of type ByteArray")
                }
            }

            if (thing is ByteBuffer) {
                val bytes = ByteArray(thing.remaining())
                thing.get(bytes)
                return Base64.encodeToString(bytes, base64Flag).trim()
            }

            // byte array to base64
            if (thing is ByteArray) {
                val base64 = Base64.encodeToString(thing, base64Flag).trim()

                // base64 to base64url
                // NOTE: "=" at the end of challenge is optional, strip it off here
                return base64.replace("+", "-").replace("/", "_").replace("=", "")
            }

            throw Error("Could not coerce to string")
        }
    }
}