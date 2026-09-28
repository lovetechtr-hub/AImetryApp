package com.djmetry.i18n

import com.djmetry.i18n.Strings.OB1_DESC
import com.djmetry.i18n.Strings.OB1_TITLE
import com.djmetry.i18n.Strings.OB2_DESC
import com.djmetry.i18n.Strings.OB2_TITLE
import com.djmetry.i18n.Strings.OB3_DESC
import com.djmetry.i18n.Strings.OB3_TITLE
import com.djmetry.i18n.Strings.OB4_DESC
import com.djmetry.i18n.Strings.OB4_TITLE
import com.djmetry.i18n.Strings.OB_CLICKS
import com.djmetry.i18n.Strings.OB_CONCERT_WHEN
import com.djmetry.i18n.Strings.OB_FIRST_TO_KNOW
import com.djmetry.i18n.Strings.OB_HOURS_AGO
import com.djmetry.i18n.Strings.OB_NOW
import com.djmetry.i18n.Strings.OB_PAID
import com.djmetry.i18n.Strings.OB_PRESAVE
import com.djmetry.i18n.Strings.OB_RISING
import com.djmetry.i18n.Strings.OB_ROLE_ARTIST
import com.djmetry.i18n.Strings.OB_ROLE_CUSTOMER
import com.djmetry.i18n.Strings.OB_ROLE_MANAGER
import com.djmetry.i18n.Strings.OB_START
import com.djmetry.i18n.Strings.OB_STATUS_ACCEPTED
import com.djmetry.i18n.Strings.OB_STATUS_DONE
import com.djmetry.i18n.Strings.OB_STATUS_ON_STAGE
import com.djmetry.i18n.Strings.OB_STATUS_ON_THE_WAY
import com.djmetry.i18n.Strings.OB_TOKEN_LINK
import com.djmetry.i18n.Strings.OB_TRACK_PLAYED
import com.djmetry.i18n.Strings.ONBOARDING_SKIP

/**
 * Тексты онбординга DJMetry (4 экрана: рейтинг, радары, инструменты артиста, букинг).
 * Подмешиваются к основным переводам в [Translations.getTranslations].
 * В OB_TRACK_PLAYED `%s` — название трека.
 */
internal object OnboardingTranslations {

    private val en = mapOf(
        ONBOARDING_SKIP to "Skip",
        OB_START to "Get started",
        OB1_TITLE to "Real DJ ranking",
        OB1_DESC to "Streaming, socials and fan votes in one index. No fake numbers.",
        OB2_TITLE to "Know it first",
        OB2_DESC to "Get a push about new releases and nearby shows the moment they happen.",
        OB3_TITLE to "Everything for artists",
        OB3_DESC to "Smart Links, BIO page and analytics. See who plays your tracks.",
        OB4_TITLE to "Booking without chaos",
        OB4_DESC to "Artists, managers and promoters: request, payment and status all the way to the stage.",
        OB_RISING to "Rising now",
        OB_NOW to "now",
        OB_HOURS_AGO to "2h ago",
        OB_FIRST_TO_KNOW to "You heard it first",
        OB_CONCERT_WHEN to "EDC Orlando · Nov 6",
        OB_CLICKS to "clicks",
        OB_PRESAVE to "Pre-save → release",
        OB_TRACK_PLAYED to "%s played in a DJ set",
        OB_ROLE_ARTIST to "Artist",
        OB_ROLE_MANAGER to "Manager",
        OB_ROLE_CUSTOMER to "Promoter",
        OB_PAID to "Paid",
        OB_STATUS_ACCEPTED to "Accepted",
        OB_STATUS_ON_THE_WAY to "Artist on the way",
        OB_STATUS_ON_STAGE to "Artist on stage",
        OB_STATUS_DONE to "Show completed",
        OB_TOKEN_LINK to "Linked by token",
    )

    private val es = mapOf(
        ONBOARDING_SKIP to "Omitir",
        OB_START to "Empezar",
        OB1_TITLE to "Ranking real de DJ",
        OB1_DESC to "Streaming, redes y votos de fans en un solo índice. Sin trampas.",
        OB2_TITLE to "Entérate primero",
        OB2_DESC to "Recibe un aviso de nuevos lanzamientos y conciertos cerca en cuanto ocurren.",
        OB3_TITLE to "Todo para artistas",
        OB3_DESC to "Smart Links, página BIO y analítica. Descubre quién pincha tus temas.",
        OB4_TITLE to "Booking sin caos",
        OB4_DESC to "Artistas, mánagers y promotores: solicitud, pago y estado hasta el escenario.",
        OB_RISING to "En ascenso",
        OB_NOW to "ahora",
        OB_HOURS_AGO to "hace 2 h",
        OB_FIRST_TO_KNOW to "Lo supiste primero",
        OB_CONCERT_WHEN to "EDC Orlando · 6 nov",
        OB_CLICKS to "clics",
        OB_PRESAVE to "Pre-save → lanzamiento",
        OB_TRACK_PLAYED to "%s sonó en un DJ set",
        OB_ROLE_ARTIST to "Artista",
        OB_ROLE_MANAGER to "Mánager",
        OB_ROLE_CUSTOMER to "Promotor",
        OB_PAID to "Pagado",
        OB_STATUS_ACCEPTED to "Aceptada",
        OB_STATUS_ON_THE_WAY to "Artista en camino",
        OB_STATUS_ON_STAGE to "Artista en el escenario",
        OB_STATUS_DONE to "Show terminado",
        OB_TOKEN_LINK to "Vinculado por token",
    )

    private val fr = mapOf(
        ONBOARDING_SKIP to "Passer",
        OB_START to "Commencer",
        OB1_TITLE to "Le vrai classement DJ",
        OB1_DESC to "Streaming, réseaux et votes des fans dans un seul indice. Sans triche.",
        OB2_TITLE to "Sois le premier informé",
        OB2_DESC to "Une notif pour chaque nouvelle sortie et chaque concert près de chez toi.",
        OB3_TITLE to "Tout pour les artistes",
        OB3_DESC to "Smart Links, page BIO et statistiques. Découvre qui joue tes morceaux.",
        OB4_TITLE to "Le booking sans chaos",
        OB4_DESC to "Artistes, managers et organisateurs : demande, paiement et statut jusqu’à la scène.",
        OB_RISING to "En hausse",
        OB_NOW to "maintenant",
        OB_HOURS_AGO to "il y a 2 h",
        OB_FIRST_TO_KNOW to "Tu l’as su en premier",
        OB_CONCERT_WHEN to "EDC Orlando · 6 nov.",
        OB_CLICKS to "clics",
        OB_PRESAVE to "Pre-save → sortie",
        OB_TRACK_PLAYED to "%s joué dans un DJ set",
        OB_ROLE_ARTIST to "Artiste",
        OB_ROLE_MANAGER to "Manager",
        OB_ROLE_CUSTOMER to "Organisateur",
        OB_PAID to "Payé",
        OB_STATUS_ACCEPTED to "Acceptée",
        OB_STATUS_ON_THE_WAY to "Artiste en route",
        OB_STATUS_ON_STAGE to "Artiste sur scène",
        OB_STATUS_DONE to "Show terminé",
        OB_TOKEN_LINK to "Lié par jeton",
    )

    private val de = mapOf(
        ONBOARDING_SKIP to "Überspringen",
        OB_START to "Los geht’s",
        OB1_TITLE to "Das echte DJ-Ranking",
        OB1_DESC to "Streaming, Social Media und Fan-Votes in einem Index. Ohne Fakes.",
        OB2_TITLE to "Als Erster wissen",
        OB2_DESC to "Push bei neuen Releases und Konzerten in deiner Nähe – sofort.",
        OB3_TITLE to "Alles für Artists",
        OB3_DESC to "Smart Links, BIO-Seite und Analytics. Sieh, wer deine Tracks spielt.",
        OB4_TITLE to "Booking ohne Chaos",
        OB4_DESC to "Artists, Manager und Veranstalter: Anfrage, Zahlung und Status bis zur Bühne.",
        OB_RISING to "Im Aufwind",
        OB_NOW to "jetzt",
        OB_HOURS_AGO to "vor 2 Std.",
        OB_FIRST_TO_KNOW to "Du wusstest es zuerst",
        OB_CONCERT_WHEN to "EDC Orlando · 6. Nov.",
        OB_CLICKS to "Klicks",
        OB_PRESAVE to "Pre-Save → Release",
        OB_TRACK_PLAYED to "%s lief in einem DJ-Set",
        OB_ROLE_ARTIST to "Artist",
        OB_ROLE_MANAGER to "Manager",
        OB_ROLE_CUSTOMER to "Veranstalter",
        OB_PAID to "Bezahlt",
        OB_STATUS_ACCEPTED to "Angenommen",
        OB_STATUS_ON_THE_WAY to "Artist unterwegs",
        OB_STATUS_ON_STAGE to "Artist auf der Bühne",
        OB_STATUS_DONE to "Auftritt beendet",
        OB_TOKEN_LINK to "Per Token verknüpft",
    )

    private val ru = mapOf(
        ONBOARDING_SKIP to "Пропустить",
        OB_START to "Начать",
        OB1_TITLE to "Реальный рейтинг DJ",
        OB1_DESC to "Стриминг, соцсети и голоса фанатов в одном индексе. Без накруток.",
        OB2_TITLE to "Узнавай раньше всех",
        OB2_DESC to "Пуш о новом релизе и концерте рядом — сразу, как это случится.",
        OB3_TITLE to "Всё для артиста",
        OB3_DESC to "Smart Links, BIO-страница и аналитика. Узнай, кто играет твои треки.",
        OB4_TITLE to "Букинг без хаоса",
        OB4_DESC to "Артисты, менеджеры и заказчики: заявка, оплата и статусы до самой сцены.",
        OB_RISING to "Растут сейчас",
        OB_NOW to "сейчас",
        OB_HOURS_AGO to "2 ч назад",
        OB_FIRST_TO_KNOW to "Ты узнал первым",
        OB_CONCERT_WHEN to "EDC Orlando · 6 нояб",
        OB_CLICKS to "кликов",
        OB_PRESAVE to "Pre-save → релиз",
        OB_TRACK_PLAYED to "%s сыграли в DJ-сете",
        OB_ROLE_ARTIST to "Артист",
        OB_ROLE_MANAGER to "Менеджер",
        OB_ROLE_CUSTOMER to "Заказчик",
        OB_PAID to "Оплачено",
        OB_STATUS_ACCEPTED to "Принята",
        OB_STATUS_ON_THE_WAY to "Артист в пути",
        OB_STATUS_ON_STAGE to "Артист на сцене",
        OB_STATUS_DONE to "Выступление завершено",
        OB_TOKEN_LINK to "Привязка по токену",
    )

    private val uk = mapOf(
        ONBOARDING_SKIP to "Пропустити",
        OB_START to "Почати",
        OB1_TITLE to "Реальний рейтинг DJ",
        OB1_DESC to "Стримінг, соцмережі та голоси фанів в одному індексі. Без накруток.",
        OB2_TITLE to "Дізнавайся першим",
        OB2_DESC to "Пуш про новий реліз і концерт поруч — одразу, як це станеться.",
        OB3_TITLE to "Усе для артиста",
        OB3_DESC to "Smart Links, BIO-сторінка та аналітика. Дізнайся, хто грає твої треки.",
        OB4_TITLE to "Букінг без хаосу",
        OB4_DESC to "Артисти, менеджери та замовники: заявка, оплата й статуси аж до сцени.",
        OB_RISING to "Ростуть зараз",
        OB_NOW to "зараз",
        OB_HOURS_AGO to "2 год тому",
        OB_FIRST_TO_KNOW to "Ти дізнався першим",
        OB_CONCERT_WHEN to "EDC Orlando · 6 лист",
        OB_CLICKS to "кліків",
        OB_PRESAVE to "Pre-save → реліз",
        OB_TRACK_PLAYED to "%s зіграли в DJ-сеті",
        OB_ROLE_ARTIST to "Артист",
        OB_ROLE_MANAGER to "Менеджер",
        OB_ROLE_CUSTOMER to "Замовник",
        OB_PAID to "Оплачено",
        OB_STATUS_ACCEPTED to "Прийнята",
        OB_STATUS_ON_THE_WAY to "Артист у дорозі",
        OB_STATUS_ON_STAGE to "Артист на сцені",
        OB_STATUS_DONE to "Виступ завершено",
        OB_TOKEN_LINK to "Прив’язка за токеном",
    )

    private val tr = mapOf(
        ONBOARDING_SKIP to "Atla",
        OB_START to "Başla",
        OB1_TITLE to "Gerçek DJ sıralaması",
        OB1_DESC to "Streaming, sosyal medya ve hayran oyları tek bir endekste. Hile yok.",
        OB2_TITLE to "İlk sen öğren",
        OB2_DESC to "Yeni çıkışlar ve yakındaki konserler için anında bildirim al.",
        OB3_TITLE to "Sanatçılar için her şey",
        OB3_DESC to "Smart Link, BIO sayfası ve analiz. Parçalarını kimin çaldığını gör.",
        OB4_TITLE to "Kaossuz booking",
        OB4_DESC to "Sanatçılar, menajerler ve organizatörler: talep, ödeme ve durum sahneye kadar.",
        OB_RISING to "Yükselenler",
        OB_NOW to "şimdi",
        OB_HOURS_AGO to "2 sa önce",
        OB_FIRST_TO_KNOW to "İlk sen öğrendin",
        OB_CONCERT_WHEN to "EDC Orlando · 6 Kas",
        OB_CLICKS to "tıklama",
        OB_PRESAVE to "Pre-save → çıkış",
        OB_TRACK_PLAYED to "%s bir DJ setinde çalındı",
        OB_ROLE_ARTIST to "Sanatçı",
        OB_ROLE_MANAGER to "Menajer",
        OB_ROLE_CUSTOMER to "Organizatör",
        OB_PAID to "Ödendi",
        OB_STATUS_ACCEPTED to "Kabul edildi",
        OB_STATUS_ON_THE_WAY to "Sanatçı yolda",
        OB_STATUS_ON_STAGE to "Sanatçı sahnede",
        OB_STATUS_DONE to "Gösteri tamamlandı",
        OB_TOKEN_LINK to "Token ile bağlı",
    )

    private val ja = mapOf(
        ONBOARDING_SKIP to "スキップ",
        OB_START to "はじめる",
        OB1_TITLE to "リアルなDJランキング",
        OB1_DESC to "ストリーミング、SNS、ファン投票をひとつの指標に。水増しなし。",
        OB2_TITLE to "誰よりも早く",
        OB2_DESC to "新作リリースや近くの公演を、発表と同時にプッシュでお知らせ。",
        OB3_TITLE to "アーティストのためのすべて",
        OB3_DESC to "Smart Link、BIOページ、分析。誰があなたの曲をプレイしたか分かります。",
        OB4_TITLE to "混乱のないブッキング",
        OB4_DESC to "アーティスト、マネージャー、主催者。依頼から支払い、ステージまで一元管理。",
        OB_RISING to "急上昇",
        OB_NOW to "たった今",
        OB_HOURS_AGO to "2時間前",
        OB_FIRST_TO_KNOW to "いち早くキャッチ",
        OB_CONCERT_WHEN to "EDC Orlando · 11月6日",
        OB_CLICKS to "クリック",
        OB_PRESAVE to "Pre-save → リリース",
        OB_TRACK_PLAYED to "%s がDJセットでプレイされました",
        OB_ROLE_ARTIST to "アーティスト",
        OB_ROLE_MANAGER to "マネージャー",
        OB_ROLE_CUSTOMER to "主催者",
        OB_PAID to "支払い済み",
        OB_STATUS_ACCEPTED to "承認済み",
        OB_STATUS_ON_THE_WAY to "アーティスト移動中",
        OB_STATUS_ON_STAGE to "アーティスト出演中",
        OB_STATUS_DONE to "公演終了",
        OB_TOKEN_LINK to "トークンで連携",
    )

    private val zhCN = mapOf(
        ONBOARDING_SKIP to "跳过",
        OB_START to "开始",
        OB1_TITLE to "真实的 DJ 排行",
        OB1_DESC to "流媒体、社交媒体和粉丝投票汇成一个指数。拒绝刷量。",
        OB2_TITLE to "抢先知道",
        OB2_DESC to "新歌发布、附近演出，第一时间推送给你。",
        OB3_TITLE to "艺人所需一应俱全",
        OB3_DESC to "Smart Link、BIO 页面和数据分析。看看谁在打你的歌。",
        OB4_TITLE to "告别混乱的演出预订",
        OB4_DESC to "艺人、经纪人和主办方：从邀约、付款到登台，状态一目了然。",
        OB_RISING to "正在上升",
        OB_NOW to "刚刚",
        OB_HOURS_AGO to "2 小时前",
        OB_FIRST_TO_KNOW to "你是第一个知道的",
        OB_CONCERT_WHEN to "EDC Orlando · 11月6日",
        OB_CLICKS to "次点击",
        OB_PRESAVE to "预存 → 发布",
        OB_TRACK_PLAYED to "%s 在 DJ 现场中被播放",
        OB_ROLE_ARTIST to "艺人",
        OB_ROLE_MANAGER to "经纪人",
        OB_ROLE_CUSTOMER to "主办方",
        OB_PAID to "已付款",
        OB_STATUS_ACCEPTED to "已接受",
        OB_STATUS_ON_THE_WAY to "艺人在路上",
        OB_STATUS_ON_STAGE to "艺人已登台",
        OB_STATUS_DONE to "演出结束",
        OB_TOKEN_LINK to "通过令牌绑定",
    )

    private val ptBR = mapOf(
        ONBOARDING_SKIP to "Pular",
        OB_START to "Começar",
        OB1_TITLE to "Ranking real de DJs",
        OB1_DESC to "Streaming, redes e votos dos fãs em um só índice. Sem números falsos.",
        OB2_TITLE to "Saiba primeiro",
        OB2_DESC to "Receba push de novos lançamentos e shows perto de você na hora.",
        OB3_TITLE to "Tudo para artistas",
        OB3_DESC to "Smart Links, página BIO e análises. Veja quem toca suas faixas.",
        OB4_TITLE to "Booking sem caos",
        OB4_DESC to "Artistas, empresários e contratantes: pedido, pagamento e status até o palco.",
        OB_RISING to "Em alta",
        OB_NOW to "agora",
        OB_HOURS_AGO to "há 2 h",
        OB_FIRST_TO_KNOW to "Você soube primeiro",
        OB_CONCERT_WHEN to "EDC Orlando · 6 nov",
        OB_CLICKS to "cliques",
        OB_PRESAVE to "Pre-save → lançamento",
        OB_TRACK_PLAYED to "%s tocou em um DJ set",
        OB_ROLE_ARTIST to "Artista",
        OB_ROLE_MANAGER to "Empresário",
        OB_ROLE_CUSTOMER to "Contratante",
        OB_PAID to "Pago",
        OB_STATUS_ACCEPTED to "Aceita",
        OB_STATUS_ON_THE_WAY to "Artista a caminho",
        OB_STATUS_ON_STAGE to "Artista no palco",
        OB_STATUS_DONE to "Show concluído",
        OB_TOKEN_LINK to "Vinculado por token",
    )

    private val it = mapOf(
        ONBOARDING_SKIP to "Salta",
        OB_START to "Inizia",
        OB1_TITLE to "La vera classifica DJ",
        OB1_DESC to "Streaming, social e voti dei fan in un unico indice. Niente trucchi.",
        OB2_TITLE to "Sappilo per primo",
        OB2_DESC to "Notifiche per nuove uscite e concerti vicino a te, appena succedono.",
        OB3_TITLE to "Tutto per gli artisti",
        OB3_DESC to "Smart Link, pagina BIO e statistiche. Scopri chi suona le tue tracce.",
        OB4_TITLE to "Booking senza caos",
        OB4_DESC to "Artisti, manager e organizzatori: richiesta, pagamento e stato fino al palco.",
        OB_RISING to "In crescita",
        OB_NOW to "ora",
        OB_HOURS_AGO to "2 h fa",
        OB_FIRST_TO_KNOW to "L’hai saputo per primo",
        OB_CONCERT_WHEN to "EDC Orlando · 6 nov",
        OB_CLICKS to "clic",
        OB_PRESAVE to "Pre-save → uscita",
        OB_TRACK_PLAYED to "%s suonata in un DJ set",
        OB_ROLE_ARTIST to "Artista",
        OB_ROLE_MANAGER to "Manager",
        OB_ROLE_CUSTOMER to "Organizzatore",
        OB_PAID to "Pagato",
        OB_STATUS_ACCEPTED to "Accettata",
        OB_STATUS_ON_THE_WAY to "Artista in viaggio",
        OB_STATUS_ON_STAGE to "Artista sul palco",
        OB_STATUS_DONE to "Show concluso",
        OB_TOKEN_LINK to "Collegato tramite token",
    )

    private val ko = mapOf(
        ONBOARDING_SKIP to "건너뛰기",
        OB_START to "시작하기",
        OB1_TITLE to "진짜 DJ 랭킹",
        OB1_DESC to "스트리밍, 소셜, 팬 투표를 하나의 지수로. 조작 없이.",
        OB2_TITLE to "누구보다 먼저",
        OB2_DESC to "새 릴리스와 근처 공연 소식을 바로 푸시로 받아보세요.",
        OB3_TITLE to "아티스트를 위한 모든 것",
        OB3_DESC to "Smart Link, BIO 페이지, 분석. 누가 내 트랙을 트는지 확인하세요.",
        OB4_TITLE to "혼란 없는 부킹",
        OB4_DESC to "아티스트, 매니저, 주최 측: 요청부터 결제, 무대까지 한곳에서.",
        OB_RISING to "급상승",
        OB_NOW to "방금",
        OB_HOURS_AGO to "2시간 전",
        OB_FIRST_TO_KNOW to "가장 먼저 알았어요",
        OB_CONCERT_WHEN to "EDC Orlando · 11월 6일",
        OB_CLICKS to "클릭",
        OB_PRESAVE to "Pre-save → 릴리스",
        OB_TRACK_PLAYED to "%s, DJ 세트에서 플레이됨",
        OB_ROLE_ARTIST to "아티스트",
        OB_ROLE_MANAGER to "매니저",
        OB_ROLE_CUSTOMER to "주최 측",
        OB_PAID to "결제 완료",
        OB_STATUS_ACCEPTED to "수락됨",
        OB_STATUS_ON_THE_WAY to "아티스트 이동 중",
        OB_STATUS_ON_STAGE to "아티스트 공연 중",
        OB_STATUS_DONE to "공연 종료",
        OB_TOKEN_LINK to "토큰으로 연결됨",
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
