package dev.dangbei2api.model

/**
 * 当贝 AI 的模型code与能力映射。
 *
 * supportThink: 该模型是否有"深度思考"版本（页面上带"深度"标记）
 * thinkCode:    思考版对应的实际 model code（当贝同一个 code，靠 chatOption 无思考字段区分，
 *               实际观察：带"深度"标记的是另一个可选条目，但复用同一 model code + 深度标记）
 */
data class DangbeiModel(
    val id: String,          // 对外暴露的 OpenAI 模型 id
    val code: String,        // 当贝接口的 model 字段
    val label: String,       // 显示名
    val supportThink: Boolean = false,
    val supportSearch: Boolean = true
)

object Models {

    /**
     * 页面实测模型列表（下拉框）：
     * deepseek-v3 / glm-5 / qwen3-235b-a22b(通义3-235B) / kimi-k2-5 / 豆包 /
     * 通义Plus / 通义QwQ / 通义Long / 豆包-1.5 / 文心4.5
     *
     * 其中带"深度"能力：glm-5、通义3-235B(qwen3-235b-a22b)、通义QwQ、豆包-1.5
     */
    val ALL: List<DangbeiModel> = listOf(
        DangbeiModel("deepseek-v3", "deepseek-v3", "DeepSeek-V3"),
        DangbeiModel("glm-5", "glm-5", "GLM-5", supportThink = true),
        DangbeiModel("qwen3-235b", "qwen3-235b-a22b", "通义3-235B", supportThink = true),
        DangbeiModel("kimi-k2-5", "kimi-k2-5", "Kimi K2-5"),
        DangbeiModel("doubao", "doubao", "豆包"),
        DangbeiModel("tongyi-plus", "tongyi-plus", "通义Plus"),
        DangbeiModel("tongyi-qwq", "tongyi-qwq", "通义QwQ", supportThink = true),
        DangbeiModel("tongyi-long", "tongyi-long", "通义Long"),
        DangbeiModel("doubao-1-5", "doubao-1-5", "豆包-1.5", supportThink = true),
        DangbeiModel("ernie-4-5", "ernie-4-5", "文心4.5")
    )

    fun byId(id: String): DangbeiModel? = ALL.firstOrNull { it.id == id }

    /** 默认展示的全部对外模型 id（含 -think / -search 变体） */
    fun openAiIds(): List<String> {
        val out = mutableListOf<String>()
        for (m in ALL) {
            out.add(m.id)
            if (m.supportThink) out.add("${m.id}-think")
            if (m.supportSearch) out.add("${m.id}-search")
            if (m.supportThink && m.supportSearch) out.add("${m.id}-think-search")
        }
        return out
    }
}
