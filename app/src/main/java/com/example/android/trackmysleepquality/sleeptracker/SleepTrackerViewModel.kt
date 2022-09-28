/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.provider.SyncStateContract.Helpers.insert
import android.provider.SyncStateContract.Helpers.update
import android.text.method.TextKeyListener.clear
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

        private var viewModelJob = Job()

        // OnCleared is called when the viewModel is destroyed
        override fun onCleared() {
                super.onCleared()
                // Cancel all coroutines started from the viewModel
                viewModelJob.cancel()
        }

        // Define a scope for the coroutine to run in
        private val uiScope = CoroutineScope(Dispatchers.Main +  viewModelJob)

        // Define a variable (tonight) to hold the current night
        private var tonight = MutableLiveData<SleepNight?>()

        // Define a variable (nights). Call getAllNights and assign to the nights variable
        private val nights = database.getAllNights()

        val nightsString = Transformations.map(nights) { nights ->
                formatNights(nights, application.resources)
        }

        val startButtonVisible = Transformations.map(tonight) {
                null == it
        }
        val stopButtonVisible = Transformations.map(tonight) {
                null != it
        }
        val clearButtonVisible = Transformations.map(nights) {
                it?.isNotEmpty()
        }

        private var _showSnackbarEvent = MutableLiveData<Boolean>()

        val showSnackBarEvent: LiveData<Boolean>
                get() = _showSnackbarEvent

        fun doneShowingSnackbar() {
                _showSnackbarEvent.value = false
        }

        private val _navigateToSleepQuality = MutableLiveData<SleepNight>()

        val navigateToSleepQuality: LiveData<SleepNight>
                get() = _navigateToSleepQuality

        fun doneNavigating() {
                _navigateToSleepQuality.value = null
        }

        // We need 'tonight' set asap so we can work with it, so we do it in an init block
        init {
            initialiseTonight()
        }

        // Using Coroutine to get 'tonight' from the DB so that we are not blocking the thread
        private fun initialiseTonight() {

                // specify the scope and launch a coroutine in that scope
                uiScope.launch {
                        tonight.value = getTonightFromDatabase()
                }
        }

        // We use the word 'suspend' here because we want to call it from inside the coroutine and not block
        private suspend fun getTonightFromDatabase(): SleepNight? {
                return withContext(Dispatchers.IO) {
                        var night = database.getTonight()

                        if (night?.endTimeMilli != night?.startTimeMilli) {
                                night = null
                        }
                        night
                }
        }

        // Click handler for the 'start' button to create a new sleepNight, insert it to the DB and assign it to tonight.
        fun onStartTracking(){
                uiScope.launch {
                        val newNight = SleepNight()
                        insert(newNight)
                        tonight.value = getTonightFromDatabase()
                }
        }

        private suspend fun insert(night: SleepNight){
                withContext(Dispatchers.IO) {
                        database.insert(night)
                }
        }

        // Click handler for the stop button
        fun onStopTracking(){
                uiScope.launch {
                        val oldNight = tonight.value ?: return@launch

                        oldNight.endTimeMilli = System.currentTimeMillis()
                        update(oldNight)
                        _navigateToSleepQuality.value = oldNight
                }
        }

        private suspend fun update(night: SleepNight){
                withContext(Dispatchers.IO) {
                        database.update(night)
                }
        }

        fun onClear() {
                uiScope.launch {
                        clear()
                        tonight.value = null
                        _showSnackbarEvent.value = true
                }
        }

        suspend fun clear() {
                withContext(Dispatchers.IO) {
                        database.clear()
                }
        }
}