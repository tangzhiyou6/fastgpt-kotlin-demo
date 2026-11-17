package c.h.c

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 非结构化提取。
 *
 * 不依赖大模型的结构化输出（response_format / json_schema），而是直接从模型返回的自由文本里
 * “尽力而为”地抽取业务字段，抽取顺序：
 *
 * 1. 去掉 ```json 代码块包裹，拿到第一个 JSON 对象；
 * 2. 解析成功则按 key 取值（如 scene）；
 * 3. 解析失败则用正则兜底匹配 `"scene"\s*:\s*"([^"]*)"`；
 * 4. 都没有则返回空值（`{"scene":""}`）。
 */
object Extractor {

    /** ```json ... ``` 代码块 */
    private val JSON_CODE_BLOCK = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```")

    /** 裸 JSON 对象 */
    private val BARE_JSON_OBJECT = Regex("\\{[\\s\\S]*}")

    /** 单字段正则兜底 */
    private val SCENE_FIELD = Regex("\"scene\"\\s*:\\s*\"([^\"]*)\"")

    /**
     * 从模型返回内容中提取业务字段。
     */
    fun extract(content: String): FusionResult {
        val json = findJsonObject(content)
            ?: return FusionResult(scene = fallbackScene(content))

        return try {
            val obj: JsonObject = JsonSupport.json.parseToJsonElement(json).jsonObject
            val scene = obj["scene"]?.let { element ->
                if (element is JsonPrimitive) element.content else element.toString()
            } ?: ""
            FusionResult(scene = scene)
        } catch (e: Exception) {
            FusionResult(scene = fallbackScene(content))
        }
    }

    /**
     * 从文本中定位第一个 JSON 对象：优先代码块，其次裸 JSON。
     */
    private fun findJsonObject(content: String): String? {
        JSON_CODE_BLOCK.find(content)?.let { return it.groupValues[1] }
        BARE_JSON_OBJECT.find(content)?.let { return it.value }
        return null
    }

    /**
     * 正则兜底抽取 scene。
     */
    private fun fallbackScene(content: String): String {
        return SCENE_FIELD.find(content)?.groupValues?.getOrNull(1) ?: ""
    }
}
