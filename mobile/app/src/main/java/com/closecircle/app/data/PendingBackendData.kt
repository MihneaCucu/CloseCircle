package com.closecircle.app.data

/**
 * Zones and zone alerts have no endpoint on `dev` yet (they are still proposals, see T-18).
 * The screens read these lists through the view model, so replacing this object with a real
 * repository does not touch the UI.
 */
object PendingBackendData {
    fun zones() = listOf(
        Zone("acasa", "Acasă", "rază 150 m", true),
        Zone("scoala", "Școala nr. 12", "rază 200 m · alerte pentru Ana", true),
        Zone("bunici", "Bunicii", "rază 300 m · fără alerte active", false),
    )

    fun alerts() = listOf(
        AlertItem("a1", AlertKind.ARRIVE, "Ana", " a ajuns la ", "Școala nr. 12", "", "07:52 · Familia Popescu", 0),
        AlertItem("a2", AlertKind.LEAVE, "Mihai", " a plecat de ", "Acasă", "", "Primită 08:10 · eveniment real 07:40", 30),
        AlertItem("a3", AlertKind.STOP, "Ioana", " a oprit partajarea", "", "", "09:15 · 1 alertă în așteptare a fost anulată", 0),
        AlertItem("a4", AlertKind.EXPIRED, "Partajarea temporară cu ", "", "Bunicii", " a expirat", "20:00 · nu te mai văd", 0),
    )
}
