package com.arcade.sudoku.data

/** Player pictures. Keys must match AVATARS in both servers. */
enum class Avatar(val key: String, val emoji: String) {
    FOX("fox", "🦊"), CAT("cat", "🐱"), DOG("dog", "🐶"), PANDA("panda", "🐼"),
    OWL("owl", "🦉"), FROG("frog", "🐸"), TIGER("tiger", "🐯"), UNICORN("unicorn", "🦄"),
    ROBOT("robot", "🤖"), ALIEN("alien", "👽"), LION("lion", "🦁"), PENGUIN("penguin", "🐧");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** Quick reactions players can send during a battle. Keys must match EMOJIS in both servers. */
enum class Reaction(val key: String, val emoji: String) {
    THUMBS_UP("thumbsup", "👍"), CLAP("clap", "👏"), FIRE("fire", "🔥"), LAUGH("laugh", "😂"),
    WOW("wow", "😮"), SWEAT("sweat", "😅"), THINK("think", "🤔"), COOL("cool", "😎"),
    HEART("heart", "❤️"), HURRY("hurry", "⏰");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}
