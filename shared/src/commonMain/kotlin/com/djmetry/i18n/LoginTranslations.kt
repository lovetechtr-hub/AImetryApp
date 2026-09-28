package com.djmetry.i18n

/**
 * Тексты экрана входа и главного экрана. В LOGIN_LEGAL плейсхолдеры {terms} и {privacy}
 * заменяются ссылками на djmetry.com/terms и /privacy, в HOME_HELLO `%s` — имя пользователя.
 */
internal object LoginTranslations {

    private val en = mapOf(
        Strings.LOGIN_HEADLINE to "Ranking, radars and booking — in one account",
        Strings.LOGIN_APPLE to "Continue with Apple",
        Strings.LOGIN_GOOGLE to "Continue with Google",
        Strings.LOGIN_FACEBOOK to "Continue with Facebook",
        Strings.LOGIN_LEGAL to "By continuing you accept the {terms} and {privacy}",
        Strings.LOGIN_TERMS to "Terms",
        Strings.LOGIN_PRIVACY to "Privacy Policy",
        Strings.LOGIN_ERROR_FAILED to "Couldn't sign in. Try again.",
        Strings.LOGIN_ERROR_BLOCKED to "This account is blocked. Contact support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "No connection. Check the internet and try again.",
        Strings.HOME_HELLO to "Hi, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Log out",
    )

    private val es = mapOf(
        Strings.LOGIN_HEADLINE to "Ranking, radares y booking en una sola cuenta",
        Strings.LOGIN_APPLE to "Continuar con Apple",
        Strings.LOGIN_GOOGLE to "Continuar con Google",
        Strings.LOGIN_FACEBOOK to "Continuar con Facebook",
        Strings.LOGIN_LEGAL to "Al continuar aceptas los {terms} y la {privacy}",
        Strings.LOGIN_TERMS to "Términos",
        Strings.LOGIN_PRIVACY to "Política de privacidad",
        Strings.LOGIN_ERROR_FAILED to "No se pudo iniciar sesión. Inténtalo de nuevo.",
        Strings.LOGIN_ERROR_BLOCKED to "Esta cuenta está bloqueada. Escribe a support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "Sin conexión. Revisa internet e inténtalo de nuevo.",
        Strings.HOME_HELLO to "Hola, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Cerrar sesión",
    )

    private val fr = mapOf(
        Strings.LOGIN_HEADLINE to "Classement, radars et booking dans un seul compte",
        Strings.LOGIN_APPLE to "Continuer avec Apple",
        Strings.LOGIN_GOOGLE to "Continuer avec Google",
        Strings.LOGIN_FACEBOOK to "Continuer avec Facebook",
        Strings.LOGIN_LEGAL to "En continuant, tu acceptes les {terms} et la {privacy}",
        Strings.LOGIN_TERMS to "Conditions",
        Strings.LOGIN_PRIVACY to "Politique de confidentialité",
        Strings.LOGIN_ERROR_FAILED to "Connexion impossible. Réessaie.",
        Strings.LOGIN_ERROR_BLOCKED to "Ce compte est bloqué. Écris à support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "Pas de connexion. Vérifie internet et réessaie.",
        Strings.HOME_HELLO to "Salut, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Se déconnecter",
    )

    private val de = mapOf(
        Strings.LOGIN_HEADLINE to "Ranking, Radare und Booking in einem Konto",
        Strings.LOGIN_APPLE to "Weiter mit Apple",
        Strings.LOGIN_GOOGLE to "Weiter mit Google",
        Strings.LOGIN_FACEBOOK to "Weiter mit Facebook",
        Strings.LOGIN_LEGAL to "Mit dem Fortfahren akzeptierst du die {terms} und die {privacy}",
        Strings.LOGIN_TERMS to "AGB",
        Strings.LOGIN_PRIVACY to "Datenschutzerklärung",
        Strings.LOGIN_ERROR_FAILED to "Anmeldung fehlgeschlagen. Versuch es noch einmal.",
        Strings.LOGIN_ERROR_BLOCKED to "Dieses Konto ist gesperrt. Schreib an support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "Keine Verbindung. Prüfe das Internet und versuch es erneut.",
        Strings.HOME_HELLO to "Hallo, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Abmelden",
    )

    private val ru = mapOf(
        Strings.LOGIN_HEADLINE to "Рейтинг, радары и букинг — в одном аккаунте",
        Strings.LOGIN_APPLE to "Войти с Apple",
        Strings.LOGIN_GOOGLE to "Войти с Google",
        Strings.LOGIN_FACEBOOK to "Войти с Facebook",
        Strings.LOGIN_LEGAL to "Продолжая, вы принимаете {terms} и {privacy}",
        Strings.LOGIN_TERMS to "Условия",
        Strings.LOGIN_PRIVACY to "Политику конфиденциальности",
        Strings.LOGIN_ERROR_FAILED to "Не удалось войти. Попробуйте ещё раз.",
        Strings.LOGIN_ERROR_BLOCKED to "Аккаунт заблокирован. Напишите на support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "Нет соединения. Проверьте интернет и попробуйте снова.",
        Strings.HOME_HELLO to "Привет, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Выйти",
    )

    private val uk = mapOf(
        Strings.LOGIN_HEADLINE to "Рейтинг, радари та букінг — в одному акаунті",
        Strings.LOGIN_APPLE to "Увійти з Apple",
        Strings.LOGIN_GOOGLE to "Увійти з Google",
        Strings.LOGIN_FACEBOOK to "Увійти з Facebook",
        Strings.LOGIN_LEGAL to "Продовжуючи, ви приймаєте {terms} та {privacy}",
        Strings.LOGIN_TERMS to "Умови",
        Strings.LOGIN_PRIVACY to "Політику конфіденційності",
        Strings.LOGIN_ERROR_FAILED to "Не вдалося увійти. Спробуйте ще раз.",
        Strings.LOGIN_ERROR_BLOCKED to "Акаунт заблоковано. Напишіть на support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "Немає з’єднання. Перевірте інтернет і спробуйте знову.",
        Strings.HOME_HELLO to "Привіт, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Вийти",
    )

    private val tr = mapOf(
        Strings.LOGIN_HEADLINE to "Sıralama, radarlar ve booking tek hesapta",
        Strings.LOGIN_APPLE to "Apple ile devam et",
        Strings.LOGIN_GOOGLE to "Google ile devam et",
        Strings.LOGIN_FACEBOOK to "Facebook ile devam et",
        Strings.LOGIN_LEGAL to "Devam ederek {terms} ve {privacy} kabul edersiniz",
        Strings.LOGIN_TERMS to "Koşulları",
        Strings.LOGIN_PRIVACY to "Gizlilik Politikasını",
        Strings.LOGIN_ERROR_FAILED to "Giriş yapılamadı. Tekrar dene.",
        Strings.LOGIN_ERROR_BLOCKED to "Bu hesap engellendi. support@djmetry.com adresine yaz.",
        Strings.LOGIN_ERROR_NETWORK to "Bağlantı yok. İnterneti kontrol edip tekrar dene.",
        Strings.HOME_HELLO to "Merhaba, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Çıkış yap",
    )

    private val ja = mapOf(
        Strings.LOGIN_HEADLINE to "ランキング、レーダー、ブッキングをひとつのアカウントで",
        Strings.LOGIN_APPLE to "Appleで続ける",
        Strings.LOGIN_GOOGLE to "Googleで続ける",
        Strings.LOGIN_FACEBOOK to "Facebookで続ける",
        Strings.LOGIN_LEGAL to "続行すると{terms}と{privacy}に同意したことになります",
        Strings.LOGIN_TERMS to "利用規約",
        Strings.LOGIN_PRIVACY to "プライバシーポリシー",
        Strings.LOGIN_ERROR_FAILED to "ログインできませんでした。もう一度お試しください。",
        Strings.LOGIN_ERROR_BLOCKED to "このアカウントはブロックされています。support@djmetry.com までご連絡ください。",
        Strings.LOGIN_ERROR_NETWORK to "接続がありません。インターネットを確認して再度お試しください。",
        Strings.HOME_HELLO to "こんにちは、%s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "ログアウト",
    )

    private val zhCN = mapOf(
        Strings.LOGIN_HEADLINE to "排行、雷达和演出预订，一个账号全搞定",
        Strings.LOGIN_APPLE to "通过 Apple 继续",
        Strings.LOGIN_GOOGLE to "通过 Google 继续",
        Strings.LOGIN_FACEBOOK to "通过 Facebook 继续",
        Strings.LOGIN_LEGAL to "继续即表示你同意{terms}和{privacy}",
        Strings.LOGIN_TERMS to "服务条款",
        Strings.LOGIN_PRIVACY to "隐私政策",
        Strings.LOGIN_ERROR_FAILED to "登录失败，请重试。",
        Strings.LOGIN_ERROR_BLOCKED to "该账号已被封禁，请联系 support@djmetry.com。",
        Strings.LOGIN_ERROR_NETWORK to "无网络连接，请检查网络后重试。",
        Strings.HOME_HELLO to "你好，%s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "退出登录",
    )

    private val ptBR = mapOf(
        Strings.LOGIN_HEADLINE to "Ranking, radares e booking em uma só conta",
        Strings.LOGIN_APPLE to "Continuar com Apple",
        Strings.LOGIN_GOOGLE to "Continuar com Google",
        Strings.LOGIN_FACEBOOK to "Continuar com Facebook",
        Strings.LOGIN_LEGAL to "Ao continuar, você aceita os {terms} e a {privacy}",
        Strings.LOGIN_TERMS to "Termos",
        Strings.LOGIN_PRIVACY to "Política de Privacidade",
        Strings.LOGIN_ERROR_FAILED to "Não foi possível entrar. Tente de novo.",
        Strings.LOGIN_ERROR_BLOCKED to "Esta conta está bloqueada. Escreva para support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "Sem conexão. Verifique a internet e tente de novo.",
        Strings.HOME_HELLO to "Oi, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Sair",
    )

    private val it = mapOf(
        Strings.LOGIN_HEADLINE to "Classifica, radar e booking in un solo account",
        Strings.LOGIN_APPLE to "Continua con Apple",
        Strings.LOGIN_GOOGLE to "Continua con Google",
        Strings.LOGIN_FACEBOOK to "Continua con Facebook",
        Strings.LOGIN_LEGAL to "Continuando accetti i {terms} e l’{privacy}",
        Strings.LOGIN_TERMS to "Termini",
        Strings.LOGIN_PRIVACY to "Informativa sulla privacy",
        Strings.LOGIN_ERROR_FAILED to "Accesso non riuscito. Riprova.",
        Strings.LOGIN_ERROR_BLOCKED to "Questo account è bloccato. Scrivi a support@djmetry.com.",
        Strings.LOGIN_ERROR_NETWORK to "Nessuna connessione. Controlla internet e riprova.",
        Strings.HOME_HELLO to "Ciao, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "Esci",
    )

    private val ko = mapOf(
        Strings.LOGIN_HEADLINE to "랭킹, 레이더, 부킹을 하나의 계정으로",
        Strings.LOGIN_APPLE to "Apple로 계속하기",
        Strings.LOGIN_GOOGLE to "Google로 계속하기",
        Strings.LOGIN_FACEBOOK to "Facebook으로 계속하기",
        Strings.LOGIN_LEGAL to "계속하면 {terms} 및 {privacy}에 동의하게 됩니다",
        Strings.LOGIN_TERMS to "이용약관",
        Strings.LOGIN_PRIVACY to "개인정보 처리방침",
        Strings.LOGIN_ERROR_FAILED to "로그인하지 못했습니다. 다시 시도하세요.",
        Strings.LOGIN_ERROR_BLOCKED to "차단된 계정입니다. support@djmetry.com으로 문의하세요.",
        Strings.LOGIN_ERROR_NETWORK to "연결이 없습니다. 인터넷을 확인하고 다시 시도하세요.",
        Strings.HOME_HELLO to "안녕하세요, %s",
        Strings.HOME_TOP to "DJMetry TOP 10",
        Strings.HOME_LOGOUT to "로그아웃",
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
