package com.djmetry.i18n

/** Вход: отмена, «Завершаем вход…», сессия истекла, понятные ошибки. */
internal object AuthFlowTranslations {

    private val ru = mapOf(
        Strings.LOGIN_CANCEL to "Отменить",
        Strings.LOGIN_FINISHING to "Завершаем вход…",
        Strings.LOGIN_WAITING_BROWSER to "Продолжите вход в браузере",
        Strings.LOGIN_SESSION_EXPIRED to "Сессия истекла — войдите снова",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "Время входа истекло — попробуйте ещё раз",
        Strings.LOGIN_ERROR_DELETED to "Этот аккаунт удалён",
        Strings.LOGIN_ERROR_TIMEOUT to "Вход не завершён — откройте его ещё раз",
    )

    private val en = mapOf(
        Strings.LOGIN_CANCEL to "Cancel",
        Strings.LOGIN_FINISHING to "Finishing sign-in…",
        Strings.LOGIN_WAITING_BROWSER to "Continue signing in in your browser",
        Strings.LOGIN_SESSION_EXPIRED to "Your session expired — please sign in again",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "Sign-in timed out — please try again",
        Strings.LOGIN_ERROR_DELETED to "This account has been deleted",
        Strings.LOGIN_ERROR_TIMEOUT to "Sign-in wasn't completed — please start again",
    )

    private val es = mapOf(
        Strings.LOGIN_CANCEL to "Cancelar",
        Strings.LOGIN_FINISHING to "Terminando el inicio de sesión…",
        Strings.LOGIN_WAITING_BROWSER to "Continúa el inicio de sesión en el navegador",
        Strings.LOGIN_SESSION_EXPIRED to "Tu sesión expiró: vuelve a iniciar sesión",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "El inicio de sesión caducó: inténtalo de nuevo",
        Strings.LOGIN_ERROR_DELETED to "Esta cuenta fue eliminada",
        Strings.LOGIN_ERROR_TIMEOUT to "No se completó el inicio de sesión: empieza de nuevo",
    )

    private val fr = mapOf(
        Strings.LOGIN_CANCEL to "Annuler",
        Strings.LOGIN_FINISHING to "Finalisation de la connexion…",
        Strings.LOGIN_WAITING_BROWSER to "Poursuivez la connexion dans le navigateur",
        Strings.LOGIN_SESSION_EXPIRED to "Votre session a expiré — reconnectez-vous",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "La connexion a expiré — réessayez",
        Strings.LOGIN_ERROR_DELETED to "Ce compte a été supprimé",
        Strings.LOGIN_ERROR_TIMEOUT to "La connexion n'a pas abouti — recommencez",
    )

    private val de = mapOf(
        Strings.LOGIN_CANCEL to "Abbrechen",
        Strings.LOGIN_FINISHING to "Anmeldung wird abgeschlossen…",
        Strings.LOGIN_WAITING_BROWSER to "Melde dich im Browser weiter an",
        Strings.LOGIN_SESSION_EXPIRED to "Deine Sitzung ist abgelaufen — bitte melde dich erneut an",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "Die Anmeldung ist abgelaufen — versuche es erneut",
        Strings.LOGIN_ERROR_DELETED to "Dieses Konto wurde gelöscht",
        Strings.LOGIN_ERROR_TIMEOUT to "Die Anmeldung wurde nicht abgeschlossen — starte neu",
    )

    private val uk = mapOf(
        Strings.LOGIN_CANCEL to "Скасувати",
        Strings.LOGIN_FINISHING to "Завершуємо вхід…",
        Strings.LOGIN_WAITING_BROWSER to "Продовжіть вхід у браузері",
        Strings.LOGIN_SESSION_EXPIRED to "Сесія закінчилася — увійдіть знову",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "Час входу минув — спробуйте ще раз",
        Strings.LOGIN_ERROR_DELETED to "Цей акаунт видалено",
        Strings.LOGIN_ERROR_TIMEOUT to "Вхід не завершено — почніть ще раз",
    )

    private val tr = mapOf(
        Strings.LOGIN_CANCEL to "İptal",
        Strings.LOGIN_FINISHING to "Giriş tamamlanıyor…",
        Strings.LOGIN_WAITING_BROWSER to "Girişe tarayıcıda devam edin",
        Strings.LOGIN_SESSION_EXPIRED to "Oturumunuz sona erdi — tekrar giriş yapın",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "Giriş süresi doldu — tekrar deneyin",
        Strings.LOGIN_ERROR_DELETED to "Bu hesap silindi",
        Strings.LOGIN_ERROR_TIMEOUT to "Giriş tamamlanmadı — yeniden başlatın",
    )

    private val ja = mapOf(
        Strings.LOGIN_CANCEL to "キャンセル",
        Strings.LOGIN_FINISHING to "ログインを完了しています…",
        Strings.LOGIN_WAITING_BROWSER to "ブラウザでログインを続けてください",
        Strings.LOGIN_SESSION_EXPIRED to "セッションの有効期限が切れました。もう一度ログインしてください",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "ログインの有効期限が切れました。もう一度お試しください",
        Strings.LOGIN_ERROR_DELETED to "このアカウントは削除されています",
        Strings.LOGIN_ERROR_TIMEOUT to "ログインが完了しませんでした。もう一度お試しください",
    )

    private val zhCN = mapOf(
        Strings.LOGIN_CANCEL to "取消",
        Strings.LOGIN_FINISHING to "正在完成登录…",
        Strings.LOGIN_WAITING_BROWSER to "请在浏览器中继续登录",
        Strings.LOGIN_SESSION_EXPIRED to "会话已过期，请重新登录",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "登录超时，请重试",
        Strings.LOGIN_ERROR_DELETED to "此账户已被删除",
        Strings.LOGIN_ERROR_TIMEOUT to "登录未完成，请重新开始",
    )

    private val ptBR = mapOf(
        Strings.LOGIN_CANCEL to "Cancelar",
        Strings.LOGIN_FINISHING to "Concluindo o login…",
        Strings.LOGIN_WAITING_BROWSER to "Continue o login no navegador",
        Strings.LOGIN_SESSION_EXPIRED to "Sua sessão expirou — entre novamente",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "O login expirou — tente novamente",
        Strings.LOGIN_ERROR_DELETED to "Esta conta foi excluída",
        Strings.LOGIN_ERROR_TIMEOUT to "O login não foi concluído — comece de novo",
    )

    private val it = mapOf(
        Strings.LOGIN_CANCEL to "Annulla",
        Strings.LOGIN_FINISHING to "Completamento dell'accesso…",
        Strings.LOGIN_WAITING_BROWSER to "Continua l'accesso nel browser",
        Strings.LOGIN_SESSION_EXPIRED to "La sessione è scaduta — accedi di nuovo",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "Accesso scaduto — riprova",
        Strings.LOGIN_ERROR_DELETED to "Questo account è stato eliminato",
        Strings.LOGIN_ERROR_TIMEOUT to "Accesso non completato — ricomincia",
    )

    private val ko = mapOf(
        Strings.LOGIN_CANCEL to "취소",
        Strings.LOGIN_FINISHING to "로그인 마무리 중…",
        Strings.LOGIN_WAITING_BROWSER to "브라우저에서 로그인을 계속하세요",
        Strings.LOGIN_SESSION_EXPIRED to "세션이 만료되었습니다. 다시 로그인하세요",
        Strings.LOGIN_ERROR_EXPIRED_CODE to "로그인 시간이 초과되었습니다. 다시 시도하세요",
        Strings.LOGIN_ERROR_DELETED to "삭제된 계정입니다",
        Strings.LOGIN_ERROR_TIMEOUT to "로그인이 완료되지 않았습니다. 다시 시작하세요",
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
