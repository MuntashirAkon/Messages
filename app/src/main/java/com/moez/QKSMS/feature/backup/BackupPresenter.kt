/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.feature.backup

import android.content.Context
import com.moez.QKSMS.R
import com.moez.QKSMS.common.base.QkPresenter
import com.moez.QKSMS.common.util.DateFormatter
import com.moez.QKSMS.common.util.extensions.makeToast
import com.moez.QKSMS.interactor.PerformBackup
import com.moez.QKSMS.manager.PermissionManager
import com.moez.QKSMS.repository.BackupRepository
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDispose
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BackupPresenter @Inject constructor(
    private val backupRepo: BackupRepository,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val performBackup: PerformBackup,
    private val permissionManager: PermissionManager
) : QkPresenter<BackupView, BackupState>(BackupState()) {

    private val storagePermissionSubject: Subject<Boolean> =
        BehaviorSubject.createDefault(permissionManager.hasStorage())

    init {
        disposables += backupRepo.getBackupProgress()
            .sample(16, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .subscribe { progress -> newState { copy(backupProgress = progress) } }

        disposables += backupRepo.getRestoreProgress()
            .sample(16, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .subscribe { progress -> newState { copy(restoreProgress = progress) } }

        disposables += storagePermissionSubject
            .distinctUntilChanged()
            .switchMap { backupRepo.getBackups() }
            .doOnNext { backups -> newState { copy(backups = backups) } }
            .map { backups -> backups.maxOfOrNull { it.date } ?: 0L }
            .map { lastBackup ->
                when (lastBackup) {
                    0L -> context.getString(R.string.backup_never)
                    else -> dateFormatter.getDetailedTimestamp(lastBackup)
                }
            }
            .startWith(context.getString(R.string.backup_loading))
            .subscribe { lastBackup -> newState { copy(lastBackup = lastBackup) } }
    }

    override fun bindIntents(view: BackupView) {
        super.bindIntents(view)

        view.activityVisible()
            .map { permissionManager.hasStorage() }
            .autoDispose(view.scope())
            .subscribe(storagePermissionSubject)

        view.restoreClicks()
            .withLatestFrom(
                backupRepo.getBackupProgress(),
                backupRepo.getRestoreProgress(),
            )
            { _, backupProgress, restoreProgress ->
                when {
                    backupProgress.running -> context.makeToast(R.string.backup_restore_error_backup)
                    restoreProgress.running -> context.makeToast(R.string.backup_restore_error_restore)
                    !permissionManager.hasStorage() -> view.requestStoragePermission()
                    else -> view.selectFile()
                }
            }
            .autoDispose(view.scope())
            .subscribe()

        view.restoreFileSelected()
            .autoDispose(view.scope())
            .subscribe { view.confirmRestore() }

        view.restoreConfirmed()
            .withLatestFrom(view.restoreFileSelected()) { _, backup -> backup }
            .autoDispose(view.scope())
            .subscribe { backup -> RestoreBackupService.start(context, backup.path) }

        view.stopRestoreClicks()
            .autoDispose(view.scope())
            .subscribe { view.stopRestore() }

        view.stopRestoreConfirmed()
            .autoDispose(view.scope())
            .subscribe { backupRepo.stopRestore() }

        view.fabClicks()
            .autoDispose(view.scope())
            .subscribe {
                if (!permissionManager.hasStorage()) {
                    view.requestStoragePermission()
                } else {
                    performBackup.execute(Unit)
                }
            }
    }

}