package com.travelneveralone.tna

import android.content.Context

/// Ticks of the packing checklist (0173), kept on the phone per trip: they work
/// with no network and survive restarts. Same rules as iOS TripPlanStore.
interface StringSetStore {
    fun get(key: String): Set<String>
    fun set(key: String, value: Set<String>)
    fun clear()
}

class PrefsStringSetStore(context: Context, name: String) : StringSetStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    // Copy: the set SharedPreferences hands out must not be modified.
    override fun get(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet().orEmpty()
    override fun set(key: String, value: Set<String>) { prefs.edit().putStringSet(key, value.toSet()).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}

class TripChecklist(private val store: StringSetStore) {
    /// One departure's packed items never show up ticked on the next trip that
    /// lists the same thing; items no longer on the list drop out.
    fun done(trip: String, list: List<String>): Set<String> = store.get(key(trip)).intersect(list.toSet())

    fun setDone(trip: String, items: Set<String>) = store.set(key(trip), items)

    /// Sign-out: the next person on this phone starts clean.
    fun clear() = store.clear()

    private fun key(trip: String) = "trip.checklist.done.$trip"
}
