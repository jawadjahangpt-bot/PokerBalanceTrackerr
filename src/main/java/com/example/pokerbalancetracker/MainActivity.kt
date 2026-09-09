package com.example.pokerbalancetracker

import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.content.*
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

data class Player(val id:String, var name:String)
data class Tx(val id:String, val playerId:String, val type:String, val amount:Double, val date:Long)

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("poker", MODE_PRIVATE) }
    private val players = mutableListOf<Player>()
    private val txs = mutableListOf<Tx>()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout

    private val bg = Color.rgb(11,16,32); private val card = Color.rgb(18,26,45)
    private val card2 = Color.rgb(24,35,59); private val text = Color.rgb(238,243,255)
    private val muted = Color.rgb(147,161,189); private val blue = Color.rgb(92,141,255)
    private val green = Color.rgb(49,208,124); private val red = Color.rgb(255,92,112)

    override fun onCreate(b: Bundle?) { super.onCreate(b); load(); build(); showDashboard() }

    private fun load() {
        val ps=prefs.getString("players","") ?: ""
        if(ps.isNotBlank()) ps.split("||").forEach{val x=it.split("|");if(x.size>=2)players.add(Player(x[0],x[1]))}
        val ts=prefs.getString("txs","") ?: ""
        if(ts.isNotBlank()) ts.split("||").forEach{val x=it.split("|");if(x.size>=4)txs.add(Tx(x[0],x[1],x[2],x[3].toDouble(),x[4].toLong()))}
    }
    private fun save(){
        prefs.edit().putString("players",players.joinToString("||"){"${it.id}|${it.name}"}).
        putString("txs",txs.joinToString("||"){"${it.id}|${it.playerId}|${it.type}|${it.amount}|${it.date}"}).apply()
    }
    private fun build(){
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);setPadding(18,18,18,10)}
        val head=TextView(this).apply{text="♠ Poker Balance Tracker";setTextColor(text);textSize=25f;setTypeface(null,1);setPadding(0,5,0,4)}
        root.addView(head, LinearLayout.LayoutParams(-1,-2))
        val sub=TextView(this).apply{text="Buy-in • Cash-out • Balance • Settlement";setTextColor(muted);textSize=13f}
        root.addView(sub)
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,16,0,10)}
        listOf("Dashboard","Players","Transactions","Settlement").forEachIndexed{ i,s->
            val v=button(s); v.setOnClickListener{when(i){0->showDashboard();1->showPlayers();2->showTx();3->showSettlement()}}
            nav.addView(v,LinearLayout.LayoutParams(0,48,1f).apply{setMargins(3,0,3,0)})
        }
        root.addView(nav)
        val scroll=ScrollView(this); content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(content)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }
    private fun button(s:String)=Button(this).apply{text=s;textSize=11f;setTextColor(Color.WHITE);setBackgroundColor(blue);isAllCaps=false}
    private fun title(s:String){content.removeAllViews();val t=TextView(this).apply{text=s;setTextColor(text);textSize=21f;setTypeface(null,1);setPadding(0,8,0,12)};content.addView(t)}
    private fun row():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(12,10,12,10);setBackgroundColor(card);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,4,0,4)}}
    private fun tv(s:String,size:Float=14f,color:Int=text)=TextView(this).apply{text=s;textSize=size;setTextColor(color);setPadding(4,2,4,2)}
    private fun money(x:Double)="৳"+String.format(Locale.US,"%,.2f",x)
    private fun balance(p:Player):Triple<Double,Double,Double>{val a=txs.filter{it.playerId==p.id};val i=a.filter{it.type=="in"}.sumOf{it.amount};val o=a.filter{it.type=="out"}.sumOf{it.amount};return Triple(i,o,i-o)}

    private fun showDashboard(){
        title("Dashboard")
        val totalIn=txs.filter{it.type=="in"}.sumOf{it.amount}; val totalOut=txs.filter{it.type=="out"}.sumOf{it.amount}
        val stats=row();stats.addView(tv("Players\n${players.size}",15f),LinearLayout.LayoutParams(0,-2,1f));stats.addView(tv("Buy-in\n${money(totalIn)}",15f,green),LinearLayout.LayoutParams(0,-2,1f));stats.addView(tv("Cash-out\n${money(totalOut)}",15f,red),LinearLayout.LayoutParams(0,-2,1f));content.addView(stats)
        players.forEach{p->val b=balance(p);val r=row();r.addView(tv(p.name,16f),LinearLayout.LayoutParams(0,-2,1f));r.addView(tv("In ${money(b.first)}\nOut ${money(b.second)}\nBalance ${money(b.third)}",13f,if(b.third>=0)green else red));content.addView(r)}
        if(players.isEmpty())content.addView(tv("No players yet. Go to Players → Add Player.",14f,muted))
    }
    private fun showPlayers(){
        title("Players")
        val input=EditText(this).apply{hint="Player name";setHintTextColor(muted);setTextColor(text);setBackgroundColor(card2)}
        content.addView(input,LinearLayout.LayoutParams(-1,55).apply{setMargins(0,0,0,8)})
        val add=button("+ Add Player");add.setOnClickListener{val n=input.text.toString().trim();if(n.isEmpty())toast("Enter a name")else if(players.any{it.name.equals(n,true)})toast("Player already exists")else{players.add(Player(UUID.randomUUID().toString(),n));input.text.clear();save();showPlayers()}}
        content.addView(add)
        players.forEach{p->val r=row();r.addView(tv(p.name,16f),LinearLayout.LayoutParams(0,-2,1f));val del=button("Delete");del.setOnClickListener{if(txs.any{x->x.playerId==p.id})toast("Delete transactions first")else{players.remove(p);save();showPlayers()}};r.addView(del,LinearLayout.LayoutParams(100,48));content.addView(r)}
    }
    private fun showTx(){
        title("New Transaction")
        if(players.isEmpty()){content.addView(tv("Add a player first.",15f,muted));return}
        val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,players.map{it.name});content.addView(sp)
        val type=Spinner(this);type.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Buy-in / Add Money","Cash-out / Withdraw"));content.addView(type)
        val amount=EditText(this).apply{hint="Amount (৳)";inputType=2;setHintTextColor(muted);setTextColor(text);setBackgroundColor(card2)};content.addView(amount,LinearLayout.LayoutParams(-1,55).apply{setMargins(0,8,0,8)})
        val add=button("Save Transaction");add.setOnClickListener{val a=amount.text.toString().toDoubleOrNull();if(a==null||a<=0)toast("Enter valid amount")else{val p=players[sp.selectedItemPosition];txs.add(0,Tx(UUID.randomUUID().toString(),p.id,if(type.selectedItemPosition==0)"in" else "out",a,System.currentTimeMillis()));save();amount.text.clear();showTx()}};content.addView(add)
        content.addView(tv("Transaction History",19f).apply{setPadding(0,22,0,8)})
        txs.forEach{t->val p=players.find{x->x.id==t.playerId};val r=row();r.addView(tv("${p?.name?:"Unknown"}\n${if(t.type=="in")"BUY-IN" else "CASH-OUT"} • ${money(t.amount)}\n${SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(Date(t.date))}",14f),LinearLayout.LayoutParams(0,-2,1f));val del=button("Delete");del.setOnClickListener{txs.remove(t);save();showTx()};r.addView(del,LinearLayout.LayoutParams(95,48));content.addView(r)}
    }
    private fun showSettlement(){
        title("Settlement")
        val credits=players.map{p->val b=balance(p).third;p to b}.filter{it.second>0.005}.map{it.first to it.second}.toMutableList()
        val debts=players.map{p->val b=balance(p).third;p to -b}.filter{it.second>0.005}.map{it.first to it.second}.toMutableList()
        var i=0;var j=0
        while(i<debts.size&&j<credits.size){val x=min(debts[i].second,credits[j].second);content.addView(row().apply{addView(tv("${debts[i].first.name}  →  ${credits[j].first.name}\n${money(x)}",17f,green))});debts[i]=debts[i].first to debts[i].second-x;credits[j]=credits[j].first to credits[j].second-x;if(debts[i].second<.005)i++;if(credits[j].second<.005)j++}
        if(i==0&&j==0)content.addView(tv("Everyone is settled up.",15f,muted))
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
