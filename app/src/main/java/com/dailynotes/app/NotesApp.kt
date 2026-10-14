package com.dailynotes.app

import android.app.Application

class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminder.createChannel(this)
    }
}
