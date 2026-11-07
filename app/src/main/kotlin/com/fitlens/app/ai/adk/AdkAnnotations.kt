package com.fitlens.app.ai.adk

/**
 * Annotation marking a Kotlin function as an ADK Tool callable by the LLM agent.
 * Mirrors com.google.adk.kt.annotations.Tool.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tool(
    val description: String = ""
)

/**
 * Annotation describing parameters for an ADK Tool function.
 * Mirrors com.google.adk.kt.annotations.Param.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Param(
    val description: String = ""
)
