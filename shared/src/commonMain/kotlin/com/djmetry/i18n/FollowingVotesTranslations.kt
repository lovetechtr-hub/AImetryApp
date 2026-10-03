package com.djmetry.i18n

/** «Подписки»: фильтр «за кого я голосовал» и отметка голоса; нейтральный текст тарифа для App Store / Google Play. */
internal object FollowingVotesTranslations {

    private val ru = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "Адреса email на вашем тарифе не показываются.",
        Strings.FOLLOW_FILTER_VOTES to "Голоса %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Вы пока ни за кого не голосовали. Голос — свайп вверх в «Открытиях».",
        Strings.FOLLOW_VOTE_BADGE to "Голос",
    )
    private val en = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "Email addresses are not shown on your plan.",
        Strings.FOLLOW_FILTER_VOTES to "Votes %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "You haven't voted for anyone yet. Vote by swiping up in Discover.",
        Strings.FOLLOW_VOTE_BADGE to "Vote",
    )
    private val es = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "Las direcciones de email no se muestran en tu plan.",
        Strings.FOLLOW_FILTER_VOTES to "Votos %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Aún no has votado a nadie. Vota deslizando hacia arriba en Descubrir.",
        Strings.FOLLOW_VOTE_BADGE to "Voto",
    )
    private val fr = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "Les adresses e-mail ne sont pas affichées avec votre formule.",
        Strings.FOLLOW_FILTER_VOTES to "Votes %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Vous n'avez encore voté pour personne. Votez en glissant vers le haut dans Découvrir.",
        Strings.FOLLOW_VOTE_BADGE to "Vote",
    )
    private val de = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "E-Mail-Adressen werden in deinem Tarif nicht angezeigt.",
        Strings.FOLLOW_FILTER_VOTES to "Stimmen %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Du hast noch für niemanden gestimmt. Abstimmen: in Entdecken nach oben wischen.",
        Strings.FOLLOW_VOTE_BADGE to "Stimme",
    )
    private val uk = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "Адреси email на вашому тарифі не показуються.",
        Strings.FOLLOW_FILTER_VOTES to "Голоси %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Ви ще ні за кого не голосували. Голос — свайп угору в «Відкриттях».",
        Strings.FOLLOW_VOTE_BADGE to "Голос",
    )
    private val tr = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "E-posta adresleri planında gösterilmiyor.",
        Strings.FOLLOW_FILTER_VOTES to "Oylar %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Henüz kimseye oy vermedin. Keşfet’te yukarı kaydırarak oy ver.",
        Strings.FOLLOW_VOTE_BADGE to "Oy",
    )
    private val ja = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "ご利用のプランではメールアドレスは表示されません。",
        Strings.FOLLOW_FILTER_VOTES to "投票 %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "まだ誰にも投票していません。「発見」で上にスワイプして投票できます。",
        Strings.FOLLOW_VOTE_BADGE to "投票",
    )
    private val zhCN = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "你的方案不显示电子邮件地址。",
        Strings.FOLLOW_FILTER_VOTES to "投票 %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "你还没有给任何人投票。在“发现”中向上滑动即可投票。",
        Strings.FOLLOW_VOTE_BADGE to "投票",
    )
    private val ptBR = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "Os endereços de e-mail não são exibidos no seu plano.",
        Strings.FOLLOW_FILTER_VOTES to "Votos %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Você ainda não votou em ninguém. Vote deslizando para cima em Descobrir.",
        Strings.FOLLOW_VOTE_BADGE to "Voto",
    )
    private val it = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "Gli indirizzi email non vengono mostrati con il tuo piano.",
        Strings.FOLLOW_FILTER_VOTES to "Voti %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "Non hai ancora votato nessuno. Vota scorrendo verso l’alto in Scopri.",
        Strings.FOLLOW_VOTE_BADGE to "Voto",
    )
    private val ko = mapOf(
        Strings.AUD_EMAILS_HIDDEN_STORE to "현재 요금제에서는 이메일 주소가 표시되지 않습니다.",
        Strings.FOLLOW_FILTER_VOTES to "투표 %1\$s/%2\$s",
        Strings.FOLLOWING_NO_VOTES to "아직 아무에게도 투표하지 않았습니다. 탐색에서 위로 스와이프해 투표하세요.",
        Strings.FOLLOW_VOTE_BADGE to "투표",
    )

    fun forLocale(locale: Locale): Map<String, String> = when (locale) {
        Locale.ENGLISH -> en
        Locale.SPANISH -> es
        Locale.FRENCH -> fr
        Locale.GERMAN -> de
        Locale.RUSSIAN -> ru
        Locale.UKRAINIAN -> uk
        Locale.TURKISH -> tr
        Locale.JAPANESE -> ja
        Locale.CHINESE_SIMPLIFIED -> zhCN
        Locale.PORTUGUESE_BR -> ptBR
        Locale.ITALIAN -> it
        Locale.KOREAN -> ko
    }
}
