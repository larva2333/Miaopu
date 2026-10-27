package dev.kiritoxd.miaopu.data

import org.json.JSONArray
import org.json.JSONObject

/** Business fields verified against successful comment requests. */
internal object CommentWritePayloads {
    /**
     * Reply body for the 9/8.0.99 comment/publish endpoint (1/8.2.58 demands a signature,
     * and the H5 m/publish path now answers "请使用客户端互动" for third-party clients).
     *
     * The gateway accepted `parentCommentId` with code 1 but dropped the association
     * (the reply never appeared under its parent), so every plausible association
     * field is sent at once: whichever the backend honours wins, the rest are ignored.
     */
    fun reply(
        target: RatingTarget,
        parentId: String,
        content: String,
        parentSubjectId: String = "",
        images: List<String> = emptyList(),
    ): String {
        require(parentId.isNotBlank())
        require(content.isNotBlank() || images.isNotEmpty())
        val parentKey = JSONObject()
            .put("subjectId", parentSubjectId)
            .put("commentId", parentId)
            .put("key", "$parentSubjectId:$parentId")
        return JSONObject().put("outBizKey", JSONObject()
            .put("outBizType", target.outBizType).put("outBizNo", target.outBizNo))
            .put("content", content.trim())
            .put("images", JSONArray(images)).put("ancillaryContents", JSONArray())
            .put("subjectId", parentSubjectId).put("source", "m")
            .put("parentCommentId", parentId)
            .put("parentCommentKey", parentKey)
            .put("rootCommentId", parentId)
            .put("replyCommentId", parentId)
            .put("quoteCommentId", parentId)
            .put("repliedCommentId", parentId)
            .put("beReplyCommentId", parentId)
            .put("parentCommentUserId", "")
            .toString()
    }

    fun light(commentId: String, subjectId: String): String {
        require(commentId.isNotBlank() && subjectId.isNotBlank())
        return JSONObject().put("commentKey", JSONObject()
            .put("commentId", commentId).put("subjectId", subjectId)).toString()
    }
}
