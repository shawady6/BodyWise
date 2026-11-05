package com.shawady.bodywise.data

/** [severe] = needs quick attention; otherwise just outside the usual range. */
data class VitalWarning(val text: String, val severe: Boolean)

/** Common adult reference ranges. A reminder to check with a doctor, not a diagnosis. */
fun vitalWarning(r: VitalReading): VitalWarning? {
    val v = r.value
    return when (r.type) {
        VitalType.BP -> {
            val d = r.value2 ?: return null
            when {
                v >= 180 || d >= 120 -> VitalWarning("الضغط عالي جدًا ⚠️ ارتاح وقيس تاني، ولو معاك صداع شديد أو ألم في الصدر أو ضيق نفس كلّم دكتور أو إسعاف فورًا.", true)
                v < 80 || d < 50 -> VitalWarning("الضغط واطي جدًا ⚠️ اقعد أو اتسطح وخد سوائل، ولو معاك دوخة شديدة أو إغماء كلّم دكتور فورًا.", true)
                v >= 140 || d >= 90 -> VitalWarning("الضغط عالي. أعد القياس بعد راحة 5 دقايق، ولو فضل عالي كلّم دكتورك.", false)
                v < 90 || d < 60 -> VitalWarning("الضغط واطي. اشرب سوائل وقوم ببطء، ولو معاك دوخة كلّم دكتورك.", false)
                else -> null
            }
        }
        VitalType.SUGAR -> when {
            v < 54 -> VitalWarning("السكر واطي جدًا ⚠️ خد حاجة سكرية فورًا (عصير أو سكر) وأعد القياس بعد 15 دقيقة، ولو مفيش تحسن اطلب مساعدة.", true)
            v >= 300 -> VitalWarning("السكر عالي جدًا ⚠️ اشرب مية وكلّم دكتورك النهاردة.", true)
            v < 70 -> VitalWarning("السكر واطي. خد حاجة سكرية وأعد القياس بعد 15 دقيقة.", false)
            r.tag == "صائم" && v >= 126 -> VitalWarning("السكر الصايم عالي. كلّم دكتورك.", false)
            r.tag == "بعد الأكل" && v >= 180 -> VitalWarning("السكر بعد الأكل عالي. كلّم دكتورك لو اتكرر.", false)
            r.tag == "عشوائي" && v >= 200 -> VitalWarning("السكر عالي. كلّم دكتورك.", false)
            else -> null
        }
        VitalType.PULSE -> when {
            v < 40 || v > 130 -> VitalWarning("النبض خارج المعدل بشكل واضح ⚠️ ارتاح وأعد القياس، ولو معاك دوخة أو ألم في الصدر كلّم دكتور فورًا.", true)
            v < 50 -> VitalWarning("النبض بطيء. لو معاك دوخة أو تعب كلّم دكتورك.", false)
            v > 100 -> VitalWarning("النبض سريع. ارتاح وأعد القياس بعد دقايق، ولو فضل عالي كلّم دكتورك.", false)
            else -> null
        }
    }
}
