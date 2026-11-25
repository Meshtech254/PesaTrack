@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ke.pesatrail.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.pesatrail.BuildConfig
import ke.pesatrail.MainActivity
import ke.pesatrail.data.*
import ke.pesatrail.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Green=Color(0xFF16836A)
private val Red=Color(0xFFC44F50)
private val zone=ZoneId.of("Africa/Nairobi")
private fun startOfMonth()=LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
private fun displayDate(time: Long)=Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm"))
private fun dateInput(text: String)=LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli()

@Composable fun PesaTrail(vm: LedgerViewModel, activity: MainActivity) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s=settings
    val dark=when(s?.theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme=if(dark) darkColorScheme(primary=Color(0xFF7DD5B8),secondary=Color(0xFFA8C8BB)) else lightColorScheme(primary=Green,secondary=Color(0xFF527467),background=Color(0xFFF6F8F7),surface=Color(0xFFFDFEFD)), shapes=Shapes(medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp))) {
        Surface(Modifier.fillMaxSize()) {
            if(s==null) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }; return@Surface }
            var unlocked by remember { mutableStateOf(false) }
            var pin by remember { mutableStateOf("") }
            var verifying by remember { mutableStateOf(false) }
            val scope=rememberCoroutineScope()
            val lockEnabled=s.pinHash.isNotBlank() || s.biometric
            LaunchedEffect(lockEnabled) { while(true) { delay(1000); if(lockEnabled && SystemClock.elapsedRealtime()-activity.lastInteraction>60_000) unlocked=false } }
            if(lockEnabled && !unlocked) {
                Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center) {
                    Icon(AppIcons.Lock,"Locked",Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
                    Text("Your money. Your space.",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(vertical=20.dp))
                    if(s.pinHash.isNotBlank()) {
                        Field("App PIN",pin,{pin=it},password=true)
                        Button(enabled=!verifying,onClick={ verifying=true; scope.launch { try { if(System.currentTimeMillis()<s.pinBlockedUntil) vm.notice.value="Too many attempts. Wait 30 seconds." else if(vm.checkPin(pin)) { unlocked=true; pin=""; activity.lastInteraction=SystemClock.elapsedRealtime() } else { pin=""; vm.notice.value="Incorrect PIN" } } finally { verifying=false } } },modifier=Modifier.fillMaxWidth()) { Text("Unlock") }
                    }
                    if(s.biometric) OutlinedButton(onClick={activity.authenticate({unlocked=true;activity.lastInteraction=SystemClock.elapsedRealtime()},{vm.notice.value=it})},modifier=Modifier.fillMaxWidth()) { Text("Use biometrics") }
                }
            } else if(!s.onboarded) Onboarding(vm) else MainScreens(vm,activity,{unlocked=false})
            val notice by vm.notice.collectAsStateWithLifecycle()
            notice?.let { AlertDialog(onDismissRequest={vm.notice.value=null},title={Text("PesaTrail")},text={Text(it)},confirmButton={TextButton(onClick={vm.notice.value=null}) { Text("OK") }}) }
        }
    }
}

@Composable private fun Onboarding(vm: LedgerViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var providers by remember { mutableStateOf(setOf<String>()) }
    var days by remember { mutableStateOf("90") }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Text("P / PesaTrail",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        LinearProgressIndicator(progress={ (step+1)/6f },modifier=Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text(listOf("A clearer picture of your money.","Private by design.","Bring your history with you.","Choose your financial sources.","How far back?","Ready to begin.")[step],style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold)
        Text(listOf("Track the money coming in, going out, and moving between your accounts. Built for Kenya. No sign-up required.","Your financial history stays in an encrypted database on your phone. No cloud, ads, analytics, or network permission. You control exports and backups.",if(BuildConfig.SMS_ENABLED) "SMS access lets PesaTrail reconstruct financial history. Android may require installer allowlisting as well as your consent. You can import a local SMS Backup & Restore XML file or add records manually." else "Import a local SMS Backup & Restore XML file or add transactions manually. This version does not request SMS access or read messages from your phone.","Select providers, or leave all unselected to scan every supported provider. Only completed transaction formats are imported. Unmatched financial messages remain reviewable.","Choose an initial range. You can change dates in the Import Center and rescan without duplicates.",if(BuildConfig.SMS_ENABLED) "Open the Import Center to grant SMS access or choose a local XML file. Your dashboard starts empty; every figure comes from your records." else "Open the Import Center to choose a local XML file, or use the + button to add a transaction. Your dashboard starts empty; every figure comes from your records.")[step],style=MaterialTheme.typography.bodyLarge)
        if(step==3) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { ParserRegistry.providers.keys.forEach { provider -> Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(provider in providers,{providers=if(it)providers+provider else providers-provider}); Text(provider) } } }
        if(step==4) Choice("History",listOf("30","90","365","All"),days,{days=it})
        if(step!=3) Spacer(Modifier.weight(1f))
        Button(onClick={ if(step<5) step++ else { vm.task{vm.preferences.text("providers",providers.joinToString("|"));vm.preferences.text("range",days);vm.preferences.bool("onboarded",true)} } },modifier=Modifier.fillMaxWidth()) { Text(if(step==5)"Open PesaTrail" else "Continue") }
        TextButton(onClick={vm.bool("onboarded",true)},modifier=Modifier.fillMaxWidth()) { Text("Skip setup · use manually") }
    }
}

@Composable private fun MainScreens(vm: LedgerViewModel, activity: MainActivity, lock: () -> Unit) {
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var importOpen by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Transaction?>(null) }
    var search by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("All") }
    var account by remember { mutableStateOf("All") }
    var category by remember { mutableStateOf("All") }
    var direction by remember { mutableStateOf("All") }
    var from by remember { mutableStateOf("") }
    var until by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(false) }
    val filtered=remember(transactions,search,provider,account,category,direction,from,until,min,max) { transactions.filter { tx ->
        (search.isBlank() || listOf(tx.party,tx.reference.orEmpty(),tx.description,tx.notes).any { it.contains(search,true) }) &&
            (provider=="All" || provider==tx.provider) && (account=="All" || account==tx.account) && (category=="All" || category==tx.category) && (direction=="All" || direction==tx.direction) &&
            (from.isBlank() || tx.occurred >= runCatching { dateInput(from) }.getOrDefault(Long.MIN_VALUE)) && (until.isBlank() || tx.occurred < runCatching { dateInput(until)+86_400_000 }.getOrDefault(Long.MAX_VALUE)) &&
            (min.isBlank() || tx.amount >= runCatching { Money.minor(min) }.getOrDefault(0)) && (max.isBlank() || tx.amount <= runCatching { Money.minor(max) }.getOrDefault(Long.MAX_VALUE))
    } }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.export(it,filtered) } }
    val labels=listOf("Home","Transactions","Accounts","Analytics","Settings")
    val icons=listOf(AppIcons.Home,AppIcons.ReceiptLong,AppIcons.AccountBalanceWallet,AppIcons.BarChart,AppIcons.Settings)
    Scaffold(topBar={ TopAppBar(title={ Column { Text(if(importOpen)"Import Center" else labels[tab],fontWeight=FontWeight.SemiBold); Text("PesaTrail · on this device",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } },navigationIcon={if(importOpen) IconButton(onClick={importOpen=false}){Icon(AppIcons.ArrowBack,"Back")}},actions={IconButton(onClick={importOpen=true}) { Icon(AppIcons.Sms,"Import history") }}) },bottomBar={NavigationBar { labels.forEachIndexed { i,label -> NavigationBarItem(selected=tab==i && !importOpen,onClick={tab=i;importOpen=false},icon={Icon(icons[i],label)},label={Text(label,maxLines=1)}) } }},floatingActionButton={ if(!importOpen && tab in 0..1) FloatingActionButton(onClick={manual=true}) { Icon(AppIcons.Add,"Add transaction") } }) { padding ->
        if(importOpen) ImportCenter(vm,activity,Modifier.padding(padding)) else LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(tab in listOf(0,1,3)) {
                item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(if(filters)"Filter your records" else if(from.isBlank() && until.isBlank())"All recorded history" else "Selected period",color=MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick={filters=!filters}){Text(if(filters)"Hide filters" else "Filters")} } }
                if(filters) item { Panel { Field("Search merchant, person, reference",search,{search=it}); Choice("Provider",listOf("All")+transactions.map{it.provider}.distinct(),provider,{provider=it}); Choice("Account",listOf("All")+accounts.map{it.id},account,{account=it}); Choice("Category",listOf("All")+(Categories.all+transactions.map{it.category}).distinct(),category,{category=it}); Choice("Direction",listOf("All","IN","OUT","INTERNAL"),direction,{direction=it}); Field("From · YYYY-MM-DD",from,{from=it}); Field("Through · YYYY-MM-DD",until,{until=it}); Field("Minimum KES",min,{min=it}); Field("Maximum KES",max,{max=it}); TextButton(onClick={search="";provider="All";account="All";category="All";direction="All";from="";until="";min="";max=""}){Text("Clear filters")}; Text("Dates use Kenya time. Use YYYY-MM-DD; amounts use decimal KES.",style=MaterialTheme.typography.bodySmall) } }
            }
            when(tab) {
                0 -> {
                    item { Summary(filtered) }
                    item { PeriodSpending(filtered) }
                    item { Text("Accounts",style=MaterialTheme.typography.titleLarge) }
                    if(accounts.isEmpty()) item { Empty("No accounts yet", "Import a local SMS backup XML or add your first transaction.") }
                    items(accounts.filterNot{it.archived}.take(3),key={it.id}) { AccountCard(it,transactions) }
                    item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("Recent activity",style=MaterialTheme.typography.titleLarge); TextButton(onClick={tab=1}){Text("See all")} } }
                    if(filtered.isEmpty()) item { Empty("Your story starts here", "Open the Import Center to reconstruct your financial history."); Button(onClick={importOpen=true},modifier=Modifier.fillMaxWidth()){Text("Import history")} }
                    items(filtered.take(6),key={it.id}) { TransactionRow(it,{detail=it}) }
                    item { Goals(vm,filtered) }
                }
                1 -> {
                    item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("${filtered.size} transactions",style=MaterialTheme.typography.titleMedium); TextButton(onClick={export.launch("pesatrail-transactions.csv")}){Text("Export filtered CSV")} } }
                    if(filtered.isEmpty()) item { Empty("No matching transactions","Try changing filters or importing a local SMS backup XML.") }
                    items(filtered,key={it.id}){TransactionRow(it,{detail=it})}
                }
                2 -> {
                    item { AccountEditor(vm,accounts) }
                    items(accounts,key={it.id}) { a -> AccountCard(a,transactions); Row { TextButton(onClick={account=a.id;tab=1;filters=true}){Text("Timeline")}; TextButton(onClick={vm.task{vm.repo.dao.saveAccount(a.copy(archived=!a.archived))}}){Text(if(a.archived)"Unarchive" else "Archive")} } }
                    item { Text("Balances are the latest SMS observation for each account, with its date. An unidentified account may contain multiple sources: assign transactions to separate accounts before relying on its balance.",style=MaterialTheme.typography.bodySmall) }
                }
                3 -> {
                    item { Summary(filtered) }
                    item { Analytics(filtered) }
                    item { Budgets(vm,filtered) }
                    item { Goals(vm,filtered) }
                }
                4 -> item { SettingsScreen(vm,activity,{importOpen=true},lock) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if(manual) ManualDialog(vm,accounts,{manual=false})
    detail?.let { tx -> TransactionDialog(transactions.firstOrNull{it.id==tx.id} ?: tx,transactions,accounts,vm,{detail=null}) }
}

@Composable private fun Summary(records: List<Transaction>) {
    val t=totals(records.map{it.entry()})
    Panel {
        Text("NET CASH FLOW",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(t.net),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Column { Text("Recorded income",style=MaterialTheme.typography.labelMedium); Text(Money.format(t.income),color=Green,fontWeight=FontWeight.SemiBold) }; Column { Text("Net expenses",style=MaterialTheme.typography.labelMedium); Text(Money.format(t.expenses),color=Red,fontWeight=FontWeight.SemiBold) } }
        HorizontalDivider(); Text("Fees ${Money.format(t.fees)} · Refunds ${Money.format(t.refunds)}",style=MaterialTheme.typography.bodySmall)
        Text("Loans and internal movements do not count as income. Fees count once. Refunds reduce expenses.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun PeriodSpending(records: List<Transaction>) {
    val today=LocalDate.now(zone)
    val starts=listOf(today,today.minusDays((today.dayOfWeek.value-1).toLong()),today.withDayOfMonth(1))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { starts.forEachIndexed { i,date ->
        val amount=totals(records.filter{it.occurred>=date.atStartOfDay(zone).toInstant().toEpochMilli()&&it.occurred<today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()}.map{it.entry()}).expenses
        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text(listOf("Today","This week","This month")[i],style=MaterialTheme.typography.labelSmall); Text(Money.format(amount),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold) } }
    } }
}
@Composable private fun TransactionRow(tx: Transaction, click: () -> Unit) {
    Card(onClick=click,modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(if(tx.direction=="IN") AppIcons.SouthWest else if(tx.direction=="INTERNAL") AppIcons.SwapHoriz else AppIcons.NorthEast,tx.direction,tint=if(tx.direction=="IN") Green else if(tx.direction=="OUT") Red else MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) { Text(tx.party,maxLines=1,fontWeight=FontWeight.Medium); Text("${tx.category} · ${displayDate(tx.occurred)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(horizontalAlignment=Alignment.End) { Text(Money.format(tx.amount),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold); if(tx.excluded)Text("Excluded",style=MaterialTheme.typography.labelSmall) }
    } }
}
@Composable private fun AccountCard(a: Account, records: List<Transaction>) {
    val list=records.filter{it.account==a.id}; val observation=list.filter{it.balance!=null}.maxByOrNull{it.occurred}
    Panel {
        Text(a.name,style=MaterialTheme.typography.titleMedium); Text(a.provider+" · "+a.kind+if(a.archived)" · Archived" else "",style=MaterialTheme.typography.bodySmall)
        Text(observation?.balance?.let(Money::format) ?: "Balance unknown",style=MaterialTheme.typography.headlineSmall)
        Text(observation?.let{"SMS-reported · ${displayDate(it.occurred)}${if(a.id.contains("unidentified"))" · account identity uncertain" else ""}"} ?: "No confirmed balance observation",style=MaterialTheme.typography.bodySmall)
        Text("In ${Money.format(list.filter{it.direction=="IN"}.sumOf{it.amount})} · Out ${Money.format(list.filter{it.direction=="OUT"}.sumOf{it.amount})}",style=MaterialTheme.typography.bodySmall)
        Text("Fees ${Money.format(totals(list.map{it.entry()}).fees)} · Last activity ${list.maxOfOrNull{it.occurred}?.let(::displayDate) ?: "None"}",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun AccountEditor(vm: LedgerViewModel, accounts: List<Account>) {
    var name by remember{mutableStateOf("")};var provider by remember{mutableStateOf("Manual")};var kind by remember{mutableStateOf("Cash wallet")};var selected by remember{mutableStateOf("New")};var target by remember{mutableStateOf("Choose target")}
    Panel { Text("Manage accounts",style=MaterialTheme.typography.titleLarge); Choice("Account",listOf("New")+accounts.map{it.id},selected,{selected=it;accounts.find{a->a.id==it}?.let{a->name=a.name;provider=a.provider;kind=a.kind}}); Field("Name",name,{name=it});Field("Provider",provider,{provider=it}); Choice("Type",listOf("M-Pesa wallet","Airtel Money wallet","Bank account","Cash wallet","Savings account","Other financial account"),kind,{kind=it}); Button(onClick={if(name.isNotBlank()) vm.task{vm.repo.dao.saveAccount(Account(if(selected=="New")UUID.randomUUID().toString() else selected,name,provider,kind))}}){Text("Save account")}; if(selected!="New") { Choice("Merge into",listOf("Choose target")+accounts.filter{it.id!=selected}.map{it.id},target,{target=it}); TextButton(onClick={if(accounts.any{it.id==target}) vm.task{vm.repo.merge(selected,target); selected="New"}}){Text("Merge account history")}; Text("Merge moves records to the chosen account. Rename and archive are reversible; merging changes account attribution.",style=MaterialTheme.typography.bodySmall) } }
}

@Composable private fun ManualDialog(vm: LedgerViewModel, accounts: List<Account>, close: () -> Unit) {
    var account by remember{mutableStateOf(accounts.firstOrNull()?.id ?: "Cash")};var party by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var direction by remember{mutableStateOf("OUT")};var category by remember{mutableStateOf("Other")};var date by remember{mutableStateOf(LocalDate.now(zone).toString())}
    val valid=runCatching{Money.minor(amount)>0 && dateInput(date)>0}.getOrDefault(false) && account.isNotBlank()
    AlertDialog(onDismissRequest=close,title={Text("Add transaction")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Field("Account name or ID",account,{account=it});if(accounts.isNotEmpty())Choice("Existing account",accounts.map{it.id},account,{account=it});Field("Person or merchant",party,{party=it});Field("Amount · KES",amount,{amount=it});Field("Date · YYYY-MM-DD",date,{date=it});Choice("Direction",listOf("IN","OUT","INTERNAL"),direction,{direction=it});Choice("Category",Categories.all,category,{category=it})}},confirmButton={Button(enabled=valid,onClick={vm.manual(account,party,amount,direction,category,dateInput(date));close()}){Text("Save")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}
@Composable private fun TransactionDialog(tx: Transaction, all: List<Transaction>, accounts: List<Account>, vm: LedgerViewModel, close: () -> Unit) {
    val goals by vm.goals.collectAsStateWithLifecycle();var goalId by remember(tx.id){mutableStateOf(tx.goalId)}
    var category by remember(tx.id){mutableStateOf(tx.category)};var notes by remember(tx.id){mutableStateOf(tx.notes)};var amount by remember(tx.id){mutableStateOf(java.math.BigDecimal.valueOf(tx.amount,2).toPlainString())};var direction by remember(tx.id){mutableStateOf(tx.direction)};var account by remember(tx.id){mutableStateOf(tx.account)};var excluded by remember(tx.id){mutableStateOf(tx.excluded)};var original by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf(false)}
    var party by remember(tx.id){mutableStateOf(tx.party)};var fee by remember(tx.id){mutableStateOf(tx.fee?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}.orEmpty())};var balance by remember(tx.id){mutableStateOf(tx.balance?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}.orEmpty())};var date by remember(tx.id){mutableStateOf(Instant.ofEpochMilli(tx.occurred).atZone(zone).toLocalDateTime().toString())};var type by remember(tx.id){mutableStateOf(tx.type)}
    val valid=runCatching{Money.minor(amount)>0 && (fee.isBlank()||Money.minor(fee)>=0) && (balance.isBlank()||Money.minor(balance)>=0) && LocalDateTime.parse(date).year>=1970}.getOrDefault(false)&&category.isNotBlank()
    val person=all.filter{it.party.equals(tx.party,true)}
    val sent=person.filter{it.direction=="OUT"}.sumOf{it.amount};val received=person.filter{it.direction=="IN"}.sumOf{it.amount}
    AlertDialog(onDismissRequest=close,title={Text(tx.party)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("${tx.provider} · ${tx.reference ?: "No reference"}\n${displayDate(tx.occurred)}${if(tx.dateInferred)" · uses SMS received time" else ""}\nReceived ${displayDate(tx.received)}\n${tx.type} · ${tx.currency}\nFees ${tx.fee?.let(Money::format) ?: "Not supplied"}\nReported balance ${tx.balance?.let(Money::format) ?: "Not supplied"}\nSender ${tx.sender}\nSource ${tx.sourceName.orEmpty()} ${tx.sourceIdentifier.orEmpty()}\nDestination ${tx.destinationName.orEmpty()} ${tx.destinationIdentifier.orEmpty()}\nConfidence ${tx.confidence}% · ${tx.importMethod}\nImported ${displayDate(tx.imported)}${if(tx.modified)" · manually corrected" else ""}",style=MaterialTheme.typography.bodySmall)
        Field("Amount KES",amount,{amount=it});Choice("Account",(accounts.map{it.id}+tx.account).distinct(),account,{account=it});Choice("Direction",listOf("IN","OUT","INTERNAL"),direction,{direction=it});Field("Category · custom names supported",category,{category=it});Choice("Suggested categories",Categories.all,category,{category=it});Field("Personal notes",notes,{notes=it});Toggle("Exclude from analytics",excluded,{excluded=it});TextButton(onClick={original=!original}){Text(if(original)"Hide source SMS" else "View source SMS")};if(original)Text(tx.original.ifBlank{"Manual entry"},style=MaterialTheme.typography.bodySmall)
        HorizontalDivider();Text("Person / merchant summary",fontWeight=FontWeight.SemiBold);Text("${person.size} transactions\nSent ${Money.format(sent)}\nReceived ${Money.format(received)}\nNet ${Money.format(received-sent)}",style=MaterialTheme.typography.bodySmall)
        person.forEach { Text("${displayDate(it.occurred)} · ${it.direction} · ${Money.format(it.amount)}",style=MaterialTheme.typography.bodySmall) }
        TextButton(onClick={deleting=true}){Text("Delete transaction",color=Red)}
        Choice("Savings allocation",listOf("None")+goals.map{it.name},goals.find{it.id==goalId}?.name ?: "None",{chosen->goalId=goals.firstOrNull{it.name==chosen}?.id})
        HorizontalDivider();Text("Correct extracted details",fontWeight=FontWeight.SemiBold);Field("Person or merchant",party,{party=it});Field("Fee KES · blank if unknown",fee,{fee=it});Field("Reported balance KES · blank if unknown",balance,{balance=it});Field("Kenya date/time · YYYY-MM-DDTHH:MM",date,{date=it});Choice("Transaction type",listOf("MANUAL","PAYMENT","RECEIVED","TRANSFER","WITHDRAWAL","DEPOSIT","AIRTIME","LOAN","LOAN_REPAYMENT","REFUND","CHARGE"),type,{type=it})
    }},confirmButton={Button(enabled=valid,onClick={vm.edit(tx.copy(amount=Money.minor(amount),fee=fee.takeIf{it.isNotBlank()}?.let(Money::minor),balance=balance.takeIf{it.isNotBlank()}?.let(Money::minor),occurred=LocalDateTime.parse(date).atZone(zone).toInstant().toEpochMilli(),dateInferred=if(date!=Instant.ofEpochMilli(tx.occurred).atZone(zone).toLocalDateTime().toString())false else tx.dateInferred,type=type,merchant=if(type=="PAYMENT"||type=="MANUAL")party else null,sourceName=if(direction=="IN")party else tx.sourceName,destinationName=if(direction!="IN")party else tx.destinationName,direction=direction,category=category,notes=notes,account=account,excluded=excluded,goalId=goalId));close()}){Text("Save changes")}},dismissButton={TextButton(onClick=close){Text("Close")}})
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("Delete this record?")},text={Text("A later SMS rescan may import it again. Exclude it from analytics to keep a record without counting it.")},confirmButton={TextButton(onClick={vm.delete(tx);close()}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=false}){Text("Cancel")}})
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) } }
@Composable private fun Empty(title: String, message: String) { Panel { Text(title,style=MaterialTheme.typography.titleMedium); Text(message,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable internal fun Field(label: String,value: String,change: (String)->Unit,password: Boolean=false) { OutlinedTextField(value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=if(password)PasswordVisualTransformation() else VisualTransformation.None,keyboardOptions=KeyboardOptions(keyboardType=if(password)KeyboardType.Password else KeyboardType.Text)) }
@Composable internal fun Choice(label: String, options: List<String>, value: String, change: (String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Box { OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()) { Text("$label: $value",maxLines=2,modifier=Modifier.weight(1f));Icon(AppIcons.ExpandMore,null) };DropdownMenu(expanded=open,onDismissRequest={open=false},modifier=Modifier.heightIn(max=320.dp)) { options.forEach { DropdownMenuItem(text={Text(it)},onClick={change(it);open=false}) } } }
}
@Composable internal fun Toggle(label: String,value: Boolean,change: (Boolean)->Unit) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f));Switch(value,onCheckedChange=change) } }

@Composable private fun ImportCenter(vm: LedgerViewModel, activity: MainActivity, modifier: Modifier) {
    val scan by vm.scan.collectAsStateWithLifecycle();val reviews by vm.reviews.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val progress by vm.fileProgress.collectAsStateWithLifecycle()
    var permission by remember{mutableStateOf(BuildConfig.SMS_ENABLED && ContextCompat.checkSelfPermission(activity,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED)}
    var receiving by remember{mutableStateOf(BuildConfig.SMS_ENABLED && ContextCompat.checkSelfPermission(activity,Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED)}
    val launcher=if(BuildConfig.SMS_ENABLED) rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){permission=it[Manifest.permission.READ_SMS]==true;receiving=it[Manifest.permission.RECEIVE_SMS]==true} else null
    var range by remember{mutableStateOf(when(vm.settings.value?.range){"30"->"Last 30 days";"365"->"Last year";"All"->"All available";else->"Last 90 days"})};var from by remember{mutableStateOf(LocalDate.now(zone).minusDays(90).toString())};var until by remember{mutableStateOf(LocalDate.now(zone).toString())}
    var reviewOpen by remember{mutableStateOf(false)}
    fun dates(): Pair<Long,Long> { val end=System.currentTimeMillis();return when(range){"Last 30 days"->end-30*86_400_000L to end;"Last 90 days"->end-90*86_400_000L to end;"Last year"->end-365*86_400_000L to end;"All available"->0L to end;else->dateInput(from) to dateInput(until)+86_400_000L-1} }
    val valid=runCatching{val d=dates();d.first<=d.second}.getOrDefault(false)
    val file=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri-> if(uri!=null && valid){val d=dates();vm.importXml(uri,d.first,d.second)}}
    LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Panel { Text("Reconstruct your financial history",style=MaterialTheme.typography.titleLarge);if(BuildConfig.SMS_ENABLED) { Text("Read SMS: ${if(permission)"Granted" else "Unavailable"}\nNew SMS: ${if(receiving)"Granted" else "Unavailable"}");Text("Android requires installer allowlisting and your consent for SMS permissions. Sideloading alone does not grant access. A local XML backup works without SMS permission.",style=MaterialTheme.typography.bodySmall);Button(onClick={launcher?.launch(arrayOf(Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS))}){Text("Request SMS access")} } else { Text("XML import and manual entry",fontWeight=FontWeight.SemiBold); Text("This version has no SMS permissions. Choose a local SMS Backup & Restore XML file below, or use the + button on Home or Transactions to add a record. Your data stays on this device.",style=MaterialTheme.typography.bodySmall) };Text("Only financial transaction formats are saved. OTPs and login codes are rejected. Unmatched messages from financial senders stay encrypted for local review.",style=MaterialTheme.typography.bodySmall) } }
        item { Panel { Choice("Historical range",listOf("Last 30 days","Last 90 days","Last year","All available","Custom"),range,{range=it});if(range=="Custom"){Field("From YYYY-MM-DD",from,{from=it});Field("Through YYYY-MM-DD",until,{until=it})};if(BuildConfig.SMS_ENABLED) Button(enabled=permission&&valid&&!busy,onClick={val d=dates();vm.scan(d.first,d.second)},modifier=Modifier.fillMaxWidth()){Text("Scan existing SMS")};OutlinedButton(enabled=valid&&!busy,onClick={file.launch(arrayOf("text/xml","application/xml","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text("Import local SMS backup XML")};Text("Choose an SMS Backup & Restore XML file with address, body, and date attributes. Files are read on this device. Choose the same file again after interruption; duplicates are skipped.",style=MaterialTheme.typography.bodySmall) } }
        item { Panel {
            Text("Import activity",style=MaterialTheme.typography.titleLarge)
            scan?.let { s -> Text(s.status,fontWeight=FontWeight.SemiBold); if(s.status in listOf("Queued","Scanning","Importing XML")) LinearProgressIndicator(Modifier.fillMaxWidth());Text("${s.processed} scanned\n${s.imported} imported · ${s.duplicates} duplicates\n${s.unsupported} unsupported · ${s.failed} failed\nLast scan ${displayDate(s.started)}");if(BuildConfig.SMS_ENABLED && (s.status.contains("resume",true)||s.status.startsWith("Paused")))OutlinedButton(onClick={vm.scan(s.from,s.until,true)},enabled=permission){Text("Resume scan")} } ?: Text("No scans yet")
            if(busy)Text(progress)
            Row{TextButton(onClick={vm.cancel()}){Text(if(BuildConfig.SMS_ENABLED) "Cancel scan" else "Cancel import")};TextButton(onClick={vm.retryReviews()}){Text("Retry unmatched")}}
            TextButton(onClick={reviewOpen=!reviewOpen}){Text("${if(reviewOpen)"Hide" else "Review"} ${reviews.size} unrecognized financial messages")}
        } }
        if(reviewOpen) items(reviews,key={it.fingerprint}) { review -> ReviewCard(vm,review) }
        item { Panel {Text("Provider rules",style=MaterialTheme.typography.titleMedium);Text(ParserRegistry.providers.keys.joinToString(" · "));Text("Provider recognition does not guarantee a format match. Review new formats and create a local rule. No data is sent to an external AI service.",style=MaterialTheme.typography.bodySmall)} }
    }
}
@Composable private fun ReviewCard(vm: LedgerViewModel, review: Review) {
    var contains by remember(review.fingerprint){mutableStateOf("")};var direction by remember{mutableStateOf("OUT")};var category by remember{mutableStateOf("Other")}
    Panel {Text("${review.sender} · ${displayDate(review.received)}",fontWeight=FontWeight.SemiBold);Text(review.reason,style=MaterialTheme.typography.bodySmall);Text(review.body);Field("Rule: message contains",contains,{contains=it});Choice("Direction",listOf("IN","OUT","INTERNAL"),direction,{direction=it});Field("Category",category,{category=it});Button(enabled=contains.length>=4&&category.isNotBlank(),onClick={vm.task{vm.repo.dao.saveRule(LocalRule(sender=review.sender,contains=contains,direction=direction,category=category));vm.repo.ingest(Sms(review.sender,review.body,review.received,review.subscription),"LOCAL_RULE")}}){Text("Save rule and retry")};Text("Rules use the standard money and completion recognizer. Unsupported structures remain reviewable; rules cannot import OTPs.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.task{vm.repo.dao.removeReview(review.fingerprint)}}){Text("Delete retained message")} }
}

@Composable private fun Budgets(vm: LedgerViewModel, records: List<Transaction>) {
    val budgets by vm.budgets.collectAsStateWithLifecycle();var category by remember{mutableStateOf("Food and groceries")};var amount by remember{mutableStateOf("")};var weekly by remember{mutableStateOf(false)}
    Panel { Text("Spending limits",style=MaterialTheme.typography.titleLarge)
        budgets.forEach { b ->
            val today=LocalDate.now(zone);val date=if(b.weekly)today.minusDays((today.dayOfWeek.value-1).toLong()) else today.withDayOfMonth(1)
            val end=if(b.weekly)date.plusWeeks(1) else date.plusMonths(1)
            val spent=totals(records.filter{it.category==b.category&&it.occurred>=date.atStartOfDay(zone).toInstant().toEpochMilli()&&it.occurred<end.atStartOfDay(zone).toInstant().toEpochMilli()}.map{it.entry()}).expenses.coerceAtLeast(0)
            Text("${b.category} · ${if(b.weekly)"weekly" else "monthly"}",fontWeight=FontWeight.Medium);LinearProgressIndicator(progress={(spent.toFloat()/b.amount).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=if(spent>=b.amount) Red else MaterialTheme.colorScheme.primary);Text("${Money.format(spent)} / ${Money.format(b.amount)}${if(spent>=b.amount)" · Limit reached" else if(spent>=b.amount*8/10)" · Approaching limit" else ""}",style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.task{vm.repo.dao.deleteBudget(b)}}){Text("Remove limit")}
        }
        Field("Category",category,{category=it});Choice("Suggestions",Categories.all,category,{category=it});Field("Limit KES",amount,{amount=it});Toggle("Weekly (Monday–Sunday)",weekly,{weekly=it});Button(enabled=runCatching{Money.minor(amount)>0}.getOrDefault(false)&&category.isNotBlank(),onClick={vm.task{vm.repo.dao.saveBudget(Budget(category,Money.minor(amount),weekly));amount=""}}){Text("Save limit")}
        Text("Budget totals follow the active analytics filters.",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun Goals(vm: LedgerViewModel, records: List<Transaction>) {
    val goals by vm.goals.collectAsStateWithLifecycle();var name by remember{mutableStateOf("")};var target by remember{mutableStateOf("")};var date by remember{mutableStateOf(LocalDate.now(zone).plusMonths(6).toString())};var show by remember{mutableStateOf(false)}
    Panel { Text("Savings goals",style=MaterialTheme.typography.titleLarge)
        goals.forEach { g ->
            // Only actual records explicitly tagged with a goal ID count as allocations.
            val allocated=records.filter{!it.excluded&&it.goalId==g.id}.sumOf{if(it.direction=="IN"||it.direction=="INTERNAL")it.amount else -it.amount}.coerceAtLeast(0)
            Text(g.name,fontWeight=FontWeight.Medium);LinearProgressIndicator(progress={(allocated.toFloat()/g.target).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("${Money.format(allocated)} allocated · Target ${Money.format(g.target)} by ${g.targetDate}\nRemaining ${Money.format((g.target-allocated).coerceAtLeast(0))}",style=MaterialTheme.typography.bodySmall);Text("Link real savings transfers or receipts from transaction details. Allocations do not prove funds are still available.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.task{vm.repo.dao.deleteGoal(g)}}){Text("Remove goal")}
        }
        TextButton(onClick={show=!show}){Text(if(show)"Close goal form" else "Add savings goal")}
        if(show){Field("Goal name",name,{name=it});Field("Target KES",target,{target=it});Field("Target date YYYY-MM-DD",date,{date=it});Button(enabled=name.isNotBlank()&&runCatching{Money.minor(target)>0&&dateInput(date)>0}.getOrDefault(false),onClick={vm.task{vm.repo.dao.saveGoal(Goal(name=name,target=Money.minor(target),targetDate=date));name="";target="";show=false}}){Text("Create goal")}}
    }
}

@Composable private fun Analytics(records: List<Transaction>) {
    var chart by remember{mutableStateOf("Spending by category")}
    val active=records.filterNot{it.excluded}
    val groups=when(chart){
        "Income vs expenses" -> totals(active.map{it.entry()}).let{listOf("Income" to it.income,"Net expenses" to it.expenses)}
        "Daily spending" -> active.groupBy{Instant.ofEpochMilli(it.occurred).atZone(zone).toLocalDate().toString()}.toSortedMap().map{(k,v)->k to totals(v.map{it.entry()}).expenses}.takeLast(30)
        "Monthly trend" -> active.groupBy{Instant.ofEpochMilli(it.occurred).atZone(zone).toLocalDate().toString().take(7)}.toSortedMap().map{(k,v)->k to totals(v.map{it.entry()}).expenses}.takeLast(12)
        "Fee trends" -> active.groupBy{Instant.ofEpochMilli(it.occurred).atZone(zone).toLocalDate().toString().take(7)}.toSortedMap().map{(k,v)->k to totals(v.map{it.entry()}).fees}.takeLast(12)
        "Spending by institution" -> active.groupBy{it.provider}.map{(k,v)->k to totals(v.map{it.entry()}).expenses}.sortedByDescending{it.second}
        else -> active.groupBy{it.category}.map{(k,v)->k to totals(v.map{it.entry()}).expenses}.filter{it.second!=0L}.sortedByDescending{it.second}
    }
    Panel {Text("Understand your money",style=MaterialTheme.typography.titleLarge);Choice("Chart",listOf("Spending by category","Income vs expenses","Daily spending","Monthly trend","Spending by institution","Biggest categories","Fee trends"),chart,{chart=it}); if(groups.isEmpty())Text("Charts appear when you add transactions.") else { TrendChart(groups);val maximum=groups.maxOf{it.second.coerceAtLeast(1)};var chosen by remember(chart){mutableStateOf<String?>(null)};groups.forEach{(label,amount)->Column(Modifier.fillMaxWidth().clickable{chosen=if(chosen==label)null else label}.padding(vertical=5.dp)) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f));Text(Money.format(amount),style=MaterialTheme.typography.bodySmall)};LinearProgressIndicator(progress={(amount.toFloat()/maximum).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(top=5.dp));if(chosen==label)Text("${active.count{if(chart.contains("institution"))it.provider==label else if(chart.contains("categor"))it.category==label else true}} matching records · tap again to collapse",style=MaterialTheme.typography.labelSmall)} }};Text("Net spending includes fees and offsets refunds. Cash withdrawals and internal transfers contribute only their fee. Chart values are KES; detailed values above use exact minor units.",style=MaterialTheme.typography.bodySmall)}
}

@Composable private fun SettingsScreen(vm: LedgerViewModel, activity: MainActivity, openImport: () -> Unit, lock: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle();val settings=s ?: return
    var pin by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var showBackup by remember{mutableStateOf(false)};var deletion by remember{mutableStateOf("")}
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->uri?.let{vm.createBackup(it,password)};password=""}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{vm.restoreBackup(it,password)};password=""}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Panel {Text("Make it yours",style=MaterialTheme.typography.titleLarge);Choice("Theme",listOf("System","Light","Dark"),settings.theme,{vm.text("theme",it)});Button(onClick=openImport,modifier=Modifier.fillMaxWidth()){Text("Open Import Center")};Choice("Provider selection",listOf("All supported")+ParserRegistry.providers.keys,if(settings.providers.isBlank())"All supported" else settings.providers,{vm.text("providers",if(it=="All supported")"" else it)});if(BuildConfig.SMS_ENABLED) { Toggle("Reconcile SMS every 12 hours",settings.reconcile,{vm.reconciliation(it)});Text("Android schedules background work. Reconciliation is best effort and requires SMS access; it is not a real-time receiver.",style=MaterialTheme.typography.bodySmall) } else Text("XML/manual version. Import a local backup when you want to refresh your history. Automatic SMS access is unavailable in this version.",style=MaterialTheme.typography.bodySmall)}
        Panel {Text("Privacy & app lock",style=MaterialTheme.typography.titleLarge);Text("Encrypted on-device storage. No network permission. App data is excluded from Android backup. Screenshots and recent-app previews are protected.",style=MaterialTheme.typography.bodySmall);Toggle("Biometric unlock",settings.biometric,{enabled->if(enabled)activity.authenticate({vm.bool("biometric",true)},{vm.notice.value=it}) else vm.bool("biometric",false)});Field("New PIN · 6–12 digits",pin,{pin=it},true);Button(enabled=pin.matches(Regex("\\d{6,12}")),onClick={vm.savePin(pin);pin=""}){Text("Set app PIN")};if(settings.pinHash.isNotBlank())TextButton(onClick={vm.text("pinHash","")}){Text("Remove PIN")};if(settings.pinHash.isNotBlank()||settings.biometric)OutlinedButton(onClick=lock){Text("Lock now")};Text("Locks after one minute of inactivity. Keep an encrypted backup: a forgotten PIN without configured biometrics requires clearing app data.",style=MaterialTheme.typography.bodySmall)}
        Panel {Text("Your data, in your hands",style=MaterialTheme.typography.titleLarge);TextButton(onClick={showBackup=!showBackup}){Text("Encrypted backup & restore")};if(showBackup){Field("Backup passphrase · 12+ characters",password,{password=it},true);Button(enabled=password.length>=12,onClick={create.launch("pesatrail-backup.pstb")}){Text("Create encrypted backup")};OutlinedButton(enabled=password.length>=12,onClick={restore.launch(arrayOf("application/octet-stream","*/*"))}){Text("Restore and merge backup")};Text("AES-256-GCM + PBKDF2-SHA256, 600,000 iterations. The passphrase is never stored. Keep it separately. Restoring merges records and skips duplicates.",style=MaterialTheme.typography.bodySmall)};OutlinedButton(onClick={deletion="history"}){Text("Delete imported history")};OutlinedButton(onClick={deletion="rebuild"}){Text("Rebuild imported history")};TextButton(onClick={deletion="all"}){Text("Delete all app data",color=Red)} }
        Panel {Text("PesaTrail ${BuildConfig.VERSION_NAME} | ${if(BuildConfig.SMS_ENABLED) "SMS enabled" else "XML/manual"}",fontWeight=FontWeight.SemiBold);Text("Private Android money tracker · Kenya\nFinancial SMS formats vary. Review uncertain records and correct account attribution before relying on reports. Exported CSV files are unencrypted. No sample data is loaded automatically.",style=MaterialTheme.typography.bodySmall)}
    }
    if(deletion.isNotBlank())AlertDialog(onDismissRequest={deletion=""},title={Text(if(deletion=="all")"Erase all application data?" else if(deletion=="rebuild")"Rebuild imported history?" else "Delete imported financial history?")},text={Text(if(deletion=="all")"This deletes the encrypted database, preferences, and protected key. Create a backup first. This cannot be undone." else "Imported transactions, source SMS, and review messages will be removed. Manual entries remain. Rebuilding requires importing your source history again.")},confirmButton={TextButton(onClick={val action=deletion;deletion="";vm.cancel();if(action=="all")vm.task{(activity.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()} else vm.task{vm.repo.db.clearImportedHistory();if(action=="rebuild")vm.notice.value="History cleared. Open the Import Center and scan or import your XML backup again."}}){Text("Delete",color=Red)}},dismissButton={TextButton(onClick={deletion=""}){Text("Cancel")}})
}

