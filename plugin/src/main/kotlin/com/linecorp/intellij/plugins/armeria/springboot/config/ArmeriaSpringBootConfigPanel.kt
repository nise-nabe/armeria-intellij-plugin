package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.message
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class ArmeriaSpringBootConfigPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {
    private var initialRefreshScheduled = false
    private var refreshGeneration = 0
    private val statusLabel = JBLabel()
    private val tableModel = ConfigTableModel()
    private val configTable = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowHeight = JBUI.scale(22)
        tableHeader.reorderingAllowed = false
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        emptyText.text = message("springboot.config.empty")
    }
    init {
        toolbar = ActionManager.getInstance().createActionToolbar("ArmeriaSpringBootConfig", DefaultActionGroup(object : DumbAwareAction(message("springboot.config.action.refresh")) {
            override fun actionPerformed(e: AnActionEvent) = refresh()
        }), true).also { it.targetComponent = this }.component
        configTable.setDefaultRenderer(Any::class.java, ConfigTableCellRenderer())
        setContent(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 0, 8)
            add(statusLabel, BorderLayout.NORTH)
            add(JBScrollPane(configTable), BorderLayout.CENTER)
        })
        statusLabel.text = message("springboot.config.summary.notLoaded")
    }
    fun scheduleInitialRefreshIfNeeded() { if (!initialRefreshScheduled) { initialRefreshScheduled = true; refresh() } }
    fun refresh() {
        val generation = ++refreshGeneration
        statusLabel.text = message("springboot.config.summary.refreshing")
        ReadAction.nonBlocking<Result<List<ArmeriaSpringBootConfigFile>>> {
            try {
                Result.success(ArmeriaSpringBootConfigCollector.collect(project))
            } catch (exception: ProcessCanceledException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
            .inSmartMode(project).expireWith(this).coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { result ->
                if (generation != refreshGeneration) {
                    return@finishOnUiThread
                }
                result.onFailure { error ->
                    tableModel.setRows(emptyList())
                    statusLabel.text = message("springboot.config.summary.error", error.message ?: error.javaClass.simpleName)
                    return@finishOnUiThread
                }
                val files = result.getOrElse { emptyList() }
                val rows = files.flatMap { file ->
                    file.entries.map { entry -> ConfigRow(file.fileName, file.filePath, entry) }
                }
                tableModel.setRows(rows)
                statusLabel.text = if (files.isEmpty()) message("springboot.config.summary.empty")
                else message("springboot.config.summary.entries", files.size, files.sumOf { configFile -> configFile.entries.size })
            }.submit(AppExecutorUtil.getAppExecutorService())
    }
    override fun dispose() = Unit
    private data class ConfigRow(val fileName: String, val filePath: String, val entry: ArmeriaSpringBootConfigEntry)
    private class ConfigTableModel : AbstractTableModel() {
        private var rows = emptyList<ConfigRow>()
        fun setRows(r: List<ConfigRow>) { rows = r; fireTableDataChanged() }
        override fun getRowCount() = rows.size
        override fun getColumnCount() = 3
        override fun getColumnName(c: Int) = when (c) {
            0 -> message("springboot.config.column.key")
            1 -> message("springboot.config.column.value")
            else -> message("springboot.config.column.file")
        }
        override fun getValueAt(r: Int, c: Int) = when (c) {
            0 -> rows[r].entry.key; 1 -> rows[r].entry.value; else -> rows[r].fileName
        }
        fun rowAt(i: Int) = rows.getOrNull(i)
    }
    private inner class ConfigTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(t: JTable, v: Any?, sel: Boolean, focus: Boolean, r: Int, c: Int): Component {
            val comp = super.getTableCellRendererComponent(t, v, sel, focus, r, c)
            val modelRow = t.convertRowIndexToModel(r)
            toolTipText = tableModel.rowAt(modelRow)?.let { row ->
                when (c) {
                    0 -> ArmeriaSpringBootConfigKeys.documentationFor(row.entry.key)
                    2 -> row.filePath
                    else -> null
                }
            }
            font = if (c == 0) t.font.deriveFont(Font.BOLD) else t.font
            return comp
        }
    }
}
