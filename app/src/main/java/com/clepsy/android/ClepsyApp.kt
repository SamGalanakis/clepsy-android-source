package com.clepsy.android

import android.app.Application

class ClepsyApp : Application() {

	lateinit var graph: AppGraph
		private set

	override fun onCreate() {
		super.onCreate()
		graph = AppGraph(this)
	}
}
