package com.lidesheng.hyperlyric.common

interface HyperLogger {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String, e: Throwable? = null)
    fun e(tag: String, msg: String, e: Throwable? = null)
}
