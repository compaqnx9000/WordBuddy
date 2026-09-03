package com.zeroglab.hotwords.data

import android.content.Context

/** Local TSV seed is retired. System catalogs now live in PostgreSQL. */
object BuiltInWordbookSeeder {
    fun ensureSystemNotebooks(db: VocabDbHelper) {
        // no-op: notebooks come from the server
    }

    fun seedWords(context: Context, db: VocabDbHelper) {
        // no-op
    }
}
