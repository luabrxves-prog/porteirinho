package com.rondasafe.app.ui.portaria

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rondasafe.app.AppTime
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.local.LocalPatrolRunEntity
import com.rondasafe.app.data.repository.PatrolOperations
import com.rondasafe.app.data.repository.RepositoryPatrolOperations
import com.rondasafe.app.data.sync.SharedSyncBus
import com.rondasafe.app.ui.admin.AppTopBar
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
private fun operations(source: PatrolOperations?): PatrolOperations {
    val app = LocalContext.current.applicationContext
    val real = remember(app) { RepositoryPatrolOperations(app) }
    return source ?: real
}

@Composable
fun SafeAvailablePatrolsScreen(
    shift: ShiftDto, onStart: (AvailablePatrolDto, PatrolRunDto) -> Unit, onEndShift: () -> Unit,
    source: PatrolOperations? = null, changes: Flow<Long> = SharedSyncBus.epoch,
) {
    val ops = operations(source)
    val scope = rememberCoroutineScope()
    var patrols by remember { mutableStateOf<List<AvailablePatrolDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var action by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(ops, changes, refresh) {
        changes.collectLatest {
            loading = true
            try { patrols = withTimeout(20_000) { ops.available() }.distinctBy { "${it.scheduleWindowId}:${it.scheduledFor}" } }
            catch (e: TimeoutCancellationException) { error = "O servidor demorou para responder. Tente atualizar." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = userFriendlyError(e, "Não foi possível carregar as rondas.") }
            finally { loading = false }
        }
    }
    Scaffold(containerColor = RondaSafeColors.Background, topBar = { AppTopBar("Rondas do horário") }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OfflineSyncStatusBanner() }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("patrol_list_error")) } }
            item { OutlinedButton(onClick = { error = null; refresh++ }, enabled = action == null) { Text("Atualizar rondas") } }
            if (!loading && patrols.isEmpty()) item { Text("Nenhuma ronda disponível neste horário.") }
            items(patrols, key = { "${it.scheduleWindowId}:${it.scheduledFor}" }) { patrol ->
                val resumable = patrol.executionStatus == "RESUMABLE"
                val available = patrol.executionStatus == "AVAILABLE" || resumable
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(patrol.patrolName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Horário previsto: ${AppTime.time(patrol.scheduledFor)}")
                        Text("${patrol.requiredPoints} pontos obrigatórios", color = RondaSafeColors.Muted)
                        Text(when (patrol.executionStatus) {
                            "RESUMABLE" -> "Sua ronda está em andamento. Continue de onde parou."
                            "IN_PROGRESS" -> "Esta ronda já está em andamento ou aguardando confirmação."
                            "COMPLETED" -> "Concluída"
                            "INCOMPLETE" -> "Incompleta"
                            else -> if (patrol.isLate) "Disponível com atraso" else "Disponível"
                        })
                        Button(
                            onClick = {
                                if (action != null) return@Button
                                action = patrol.scheduleWindowId
                                error = null
                                scope.launch {
                                    try { onStart(patrol, withTimeout(25_000) { ops.start(shift.shiftId, patrol) }) }
                                    catch (e: TimeoutCancellationException) { error = "Início salvo ou pendente. Atualize a lista antes de repetir."; refresh++ }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { error = userFriendlyError(e, "Não foi possível iniciar a ronda.") }
                                    finally { action = null }
                                }
                            }, enabled = available && action == null,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("start_${patrol.scheduleWindowId}"),
                        ) { Text(if (action == patrol.scheduleWindowId) "Confirmando..." else if (resumable) "Continuar ronda" else "Iniciar ronda") }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        if (action != null) return@OutlinedButton
                        action = "end"
                        scope.launch {
                            try { withTimeout(25_000) { ops.endShift(shift.shiftId) }; onEndShift() }
                            catch (e: TimeoutCancellationException) { error = "Não foi possível confirmar o encerramento. Verifique as pendências." }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = userFriendlyError(e, "Não foi possível encerrar o turno.") }
                            finally { action = null }
                        }
                    }, enabled = action == null, modifier = Modifier.fillMaxWidth().testTag("end_shift"),
                ) { Text("Encerrar turno") }
            }
        }
    }
}

@Composable
fun SafePatrolScannerScreen(
    run: PatrolRunDto, patrolName: String, onFinished: (FinishPatrolDto) -> Unit,
    source: PatrolOperations? = null,
    camera: @Composable (Modifier, Boolean, (String) -> Unit) -> Unit = { modifier, enabled, callback -> SafeQrCamera(modifier, enabled, callback) },
) {
    val ops = operations(source)
    val scope = rememberCoroutineScope()
    val local by remember(ops, run.runId) { ops.observeRun(run.runId) }.collectAsState(initial = null)
    var processing by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var occurrenceSaving by remember { mutableStateOf(false) }
    var occurrenceOpen by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var scanned by remember { mutableIntStateOf(0) }
    val visited = local?.visitedPoints ?: scanned
    val total = local?.requiredPoints ?: run.requiredPoints
    val busy = processing || finishing || occurrenceSaving
    Scaffold(containerColor = RondaSafeColors.Background, topBar = { AppTopBar(patrolName) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OfflineSyncStatusBanner(Modifier.padding(horizontal = 18.dp))
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$visited/$total pontos registrados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("patrol_progress"))
                LinearProgressIndicator(progress = { if (total > 0) (visited.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                message?.let { Text(it, modifier = Modifier.testTag("scan_message")) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("scanner_error")) }
            }
            camera(Modifier.fillMaxWidth().weight(1f).background(Color.Black), !busy && !occurrenceOpen) { qr ->
                if (!busy && !occurrenceOpen) {
                    processing = true
                    error = null
                    scope.launch {
                        try {
                            val result = withTimeout(25_000) { ops.scan(run.runId, qr, android.os.SystemClock.elapsedRealtime()) }
                            scanned = result.visitedPoints
                            message = when (result.scanResult) {
                                "ACCEPTED" -> if (result.synced) "Ponto confirmado pelo servidor." else "Leitura salva neste aparelho. Aguardando sincronização."
                                "DUPLICATE" -> "Este ponto já foi registrado."
                                "REVOKED_QR" -> "Este QR Code foi substituído e não é mais válido."
                                "NOT_IN_ROUND" -> "Este ponto não pertence a esta ronda."
                                else -> "QR Code não reconhecido."
                            }
                        } catch (e: TimeoutCancellationException) { error = "A leitura não foi confirmada. Verifique as pendências antes de repetir." }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = userFriendlyError(e, "Não foi possível registrar o ponto.") }
                        finally { processing = false }
                    }
                }
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { occurrenceOpen = true; error = null }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("report_occurrence")) { Text("Registrar ocorrência") }
                Button(
                    onClick = {
                        if (busy) return@Button
                        finishing = true
                        error = null
                        scope.launch {
                            try { onFinished(withTimeout(25_000) { ops.finish(run.runId) }) }
                            catch (e: TimeoutCancellationException) { error = "A finalização ainda não foi confirmada. Verifique a sincronização." }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = userFriendlyError(e, "Não foi possível finalizar a ronda.") }
                            finally { finishing = false }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("finish_patrol"),
                ) { Text(if (finishing) "Confirmando finalização..." else "Finalizar ronda") }
            }
        }
    }
    if (occurrenceOpen) AlertDialog(
        onDismissRequest = { if (!occurrenceSaving) occurrenceOpen = false },
        title = { Text("Registrar ocorrência") },
        text = {
            Column {
                OutlinedTextField(description, onValueChange = { if (!occurrenceSaving) description = it.take(1000) }, label = { Text("Descrição") }, modifier = Modifier.testTag("occurrence_description"))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = { occurrenceOpen = false }, enabled = !occurrenceSaving) { Text("Cancelar") } },
        confirmButton = {
            Button(onClick = {
                if (occurrenceSaving) return@Button
                occurrenceSaving = true
                scope.launch {
                    try {
                        val synced = withTimeout(25_000) { ops.report(run.runId, description) }
                        message = if (synced) "Ocorrência confirmada pelo servidor." else "Ocorrência salva neste aparelho. Aguardando sincronização."
                        description = ""; occurrenceOpen = false
                    } catch (e: TimeoutCancellationException) { error = "A ocorrência não foi confirmada. Verifique as pendências." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = userFriendlyError(e, "Não foi possível registrar a ocorrência.") }
                    finally { occurrenceSaving = false }
                }
            }, enabled = description.trim().length >= 3 && !occurrenceSaving, modifier = Modifier.testTag("save_occurrence")) { Text(if (occurrenceSaving) "Enviando..." else "Salvar ocorrência") }
        },
    )
}

@Composable
fun SafePatrolFinishedScreen(result: FinishPatrolDto, onDone: () -> Unit, runClientEventId: String? = null, source: PatrolOperations? = null) {
    val ops = operations(source)
    val flow = remember(ops, runClientEventId) { runClientEventId?.let(ops::observeRun) ?: flowOf<LocalPatrolRunEntity?>(null) }
    val local by flow.collectAsState(initial = null)
    val serverFinal = local?.finalStatus?.takeIf { local?.syncState == "SYNCED" && it in setOf("COMPLETED", "INCOMPLETE") }
    val confirmed = result.synced || serverFinal != null
    val status = serverFinal ?: result.status
    val failed = !confirmed && status == "SYNC_FAILED"
    val title = when {
        failed -> "Finalização não confirmada"
        !confirmed -> "Ronda salva no aparelho"
        status == "COMPLETED" -> "Ronda concluída"
        else -> "Ronda incompleta"
    }
    Column(Modifier.fillMaxSize().background(RondaSafeColors.Background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OfflineSyncStatusBanner()
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("${if (serverFinal != null) local?.visitedPoints else result.visitedPoints}/${if (serverFinal != null) local?.requiredPoints else result.totalPoints} pontos registrados")
        if (!confirmed) {
            Spacer(Modifier.height(10.dp))
            Text(if (failed) "O registro foi preservado para revisão. Peça ao administrador para verificar a falha de sincronização." else "Aguardando sincronização. A conclusão depende da confirmação do servidor.", textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Concluir") }
        Spacer(Modifier.weight(1f))
    }
}
