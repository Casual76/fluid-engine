package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.glass.field.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import kotlin.math.*

/** Conducente condiviso: orologio, alimentazione della goccia, materiale e osservazione di ogni frame. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal object MorphRobustnessRun {
  private class Clock : MonotonicFrameClock {
    val frames = Channel<Long>(Channel.UNLIMITED)
    override suspend fun <R> withFrameNanos(onFrame: (Long)->R): R = onFrame(frames.receive())
  }
  data class Result(val issues: Map<String,String>, val frames: List<String>, val refinedAway: Int)

  fun execute(original: MorphRobustnessCases.Case, variant: String, hz: Int, dark: Boolean,
    stageWidth: Float? = null, density: Float = 1f, gallery: Boolean = false, precision: Int = 24): Result {
    val issues = linkedMapOf<String,String>(); val counts = mutableMapOf<String,Int>()
    val frames = mutableListOf<String>()
    var refinedAway=0
    runTest {
      val a=original.from.frame; val b=original.to.frame; val c=MorphRobustnessCases.third.frame
      val box=Rect(minOf(a.left,b.left,c.left),minOf(a.top,b.top,c.top),maxOf(a.right,b.right,c.right),maxOf(a.bottom,b.bottom,c.bottom))
      val scaleFit=stageWidth?.let { (it-32f)/maxOf(box.width,box.height,1f) } ?: 1f
      val case=if(stageWidth==null) original else original.copy(from=fit(original.from,box,scaleFit),to=fit(original.to,box,scaleFit))
      val third=if(stageWidth==null) MorphRobustnessCases.third else fit(MorphRobustnessCases.third,box,scaleFit)
      val liquidOn=variant.startsWith("goccia") || gallery
      val action=if(variant=="goccia") "diretto" else variant.removePrefix("goccia-")
      val state=FluidPhysicsState(case.from).apply { liquidAllowed=liquidOn; liquidBody=true; this.density=density }
      val clock=Clock(); val oracle=MorphRobustnessTest(case,variant,hz,dark)
      val source=if(gallery) PhysicsMaterialSource(null,GlassDefaults.optics(GlassRole.Floating).copy(
        refractionHeight=24.dp,refractionAmount=40.dp,depthEffect=true,highlightWidth=1.2.dp,highlightAlpha=.75f).sanitized())
        else PhysicsMaterialSource(GlassDefaults.buttonOptics().sanitized(),GlassDefaults.panelOptics(compact=true).sanitized())
      val veil=PhysicsLargeVeil(); val monitor=MorphRobustnessContinuity()
      val labels=listOf("area","baricentro-x","baricentro-y","velo-a","velo-r","velo-g","velo-b","sfocatura","rifrazione","ombra-contatto","ombra-ambiente")
      val normalization=maxOf(case.from.frame.maxDimension,case.to.frame.maxDimension,third.frame.maxDimension,
        (case.from.frame.center-case.to.frame.center).getDistance(),1f)
      // Un allarme geometrico dell'integratore 24x24 deve sopravvivere alla verifica 96x96
      // sugli stessi due/tre piani congelati. Non si allenta la soglia per far passare il caso.
      class Proof(val time:Long,val plan:PhysicsRenderPlan) {
        val fine by lazy { oracle.metrics(plan,false,96).let { floatArrayOf(it[0]/(normalization*normalization),it[1]/normalization,it[2]/normalization) } }
      }
      val history=mutableListOf<Proof>()
      var time=0L; var frame=-1; var changed=false; var target=case.to
      // La miscela ottica è una politica esplicita del banco: una ridirezione eredita il valore corrente.
      // La pagina del tablet, invece, usa un'ottica Floating costante (gallery=true).
      var blendFrom=0f; var blendTo=1f; var blend=0f
      fun report(kind:String, detail:String) {
        val n=counts.getOrDefault(kind,0)+1; counts[kind]=n
        if(kind !in issues) issues[kind]="primo=$frame ms=${time/1e6} $detail"
        else if(n==2) issues[kind]=issues.getValue(kind)+"; altri campioni"
      }
      fun sample() {
        val p=state.ensurePlan(); oracle.finite(p)
        assertTrue(p.pieceCount in 0..PhysicsMaxPieces); assertTrue(p.vertCount in 0..PhysicsMaxRingVertices)
        var intersections=0
        if(p.mode==PlanModePoly) {
          intersections=crossings(p.verts,p.vertCount)
          if(intersections>0) report("contorno-incroci","lati=$intersections bounds=${p.bounds}")
          if(signedArea(List(p.vertCount){androidx.compose.ui.geometry.Offset(p.verts[it*2],p.verts[it*2+1])}) < -.001f)
            report("contorno-verso","area negativa")
        }
        val m=oracle.metrics(p,validateContour=false,subdivisions=precision)
        val short=physicsRecipeShortPx(p)/density
        val now=glassLiquidLargeVeilGain(short)
        // morphTo non usa la guida esterna: a riposo il contratto nuovo posa subito il velo.
        val meta=state.journeyTargetNow()
        val large=if(meta==null) veil.settle(now,time) else veil.next(
          state.journeyOriginNow()?.let { glassLiquidLargeVeilGain(physicsFormShortPx(it)/density) } ?: now,
          glassLiquidLargeVeilGain(physicsFormShortPx(meta)/density),time)
        val recipe=physicsLiquidRecipe(if(gallery) GlassRole.Floating else GlassRole.Modal,source.at(if(gallery) 1f else blend),short,
          dark,1f,1f,0f,GlassTint(Color.Transparent,Color.Transparent,Color.Transparent,GlassTintMode.Neutral),null,1f,false,state.liquid.lens,largeVeil=large)
        val inputs=physicsLiquidInputs(p,recipe,1f,density,0f); oracle.finite(inputs); oracle.finite(recipe)
        val color=recipe.veilColor
        val values=floatArrayOf(m[0]/(normalization*normalization),m[1]/normalization,m[2]/normalization,
          color.alpha,color.red,color.green,color.blue,recipe.blurDp/50f,recipe.refractionDp/50f,
          recipe.shadowContactAlpha*recipe.shadowColor.alpha,recipe.shadowAmbientAlpha*recipe.shadowColor.alpha)
        assertTrue(values.all { it.isFinite() })
        val current=Proof(time,freezeGeometry(p))
        monitor.sample(time,values,labels) { kind,detail ->
          val field=labels.indexOf(kind.substringAfter(':'))
          var confirmed=true
          var precise=Float.NaN
          if(field in 0..2 && history.isNotEmpty()) {
            val prev=history.last(); val dt=(time-prev.time)/1e9f
            val delta=current.fine[field]-prev.fine[field]
            when {
              dt==0f -> { precise=delta; confirmed=abs(delta)>.001f }
              kind.startsWith("esplosione:") -> { precise=delta; confirmed=abs(delta)>20f*dt+.001f }
              kind.startsWith("accelerazione:") && history.size>=2 -> {
                val older=history[history.lastIndex-1]; val before=(prev.time-older.time)/1e9f
                precise=2f*(delta/dt-(prev.fine[field]-older.fine[field])/before)/(dt+before)
                val limit=400f+(.001f/dt+.001f/before)/((dt+before)/2f)
                confirmed=abs(precise)>limit
              }
            }
          }
          if(confirmed) report(kind,"$detail; verifica96=$precise") else refinedAway++
        }
        // Il passo zero del monitor non sostituisce il campione precedente.
        if(history.isEmpty() || time>history.last().time) { history+=current; if(history.size>2) history.removeAt(0) }
        if(gallery) {
          val outside=stageWidth!=null && (p.bounds.left<0f || p.bounds.top<0f || p.bounds.right>stageWidth || p.bounds.bottom>stageWidth)
          frames += "frame=$frame ms=${time/1e6} progress=${state.progress} cambio=$changed mode=${p.mode} incroci=$intersections concavita=${if(p.vertCount>2) concavity(p.verts,p.vertCount) else 0f} bounds=${p.bounds} fuoriPalco=$outside " +
            "rest=${state.dropRestSlabNow()?.frame} reach=${p.dropReach} settle=${p.dropSettle} kernel=${inputs.kernel} " +
            "ringBbox=${inputs.ring.bbox.joinToString()} restRect=${inputs.ring.restCenterX},${inputs.ring.restCenterY},${inputs.ring.restHalfWidth},${inputs.ring.restHalfHeight} " +
            "velo=${values.slice(3..6)} ombra=${values.slice(9..10)}"
        }
      }
      // Prima fotografia: vero riposo PRECEDENTE, prima di installare qualunque morphTo.
      sample()
      val jobs=mutableListOf(launch(clock){state.morphTo(target)})
      runCurrent()
      if(action=="doppio") {
        target=third; blendTo=.5f
        jobs += launch(clock){state.morphTo(target)}; runCurrent()
        sample() // due richieste allo stesso istante: nessun dt inventato.
      }
      val rest=FloatArray(state.liquid.points*2)
      for(i in 0 until hz*8) {
        frame=i
        time += when { action=="zero" && i==8 -> 0L; action=="irregolare" && i==8 -> 100_000_000L; else -> 1_000_000_000L/hz }
        clock.frames.send(time); runCurrent()
        if(liquidOn) {
          val slab=state.dropRestSlabNow()
          if(slab==null) state.liquid.reset() else {
            sampleRestContour(slab,state.liquid.points,rest)
            val anchor=state.dropAnchorNow()
            state.liquid.step(time,rest,anchor.x,anchor.y,FluidMotion.DropAnchorReachDp*density,density)
          }
        }
        blend=blendFrom+(blendTo-blendFrom)*state.progress.coerceIn(0f,1f)
        sample()
        val threshold=when { action.endsWith("25") -> .25f; action.endsWith("50") -> .5f; else -> .75f }
        if(!changed && (action.startsWith("inverti") || action.startsWith("terza") || action.startsWith("interrompi")) && state.progress>=threshold) {
          changed=true
          if(action.startsWith("interrompi")) {
            jobs.last().cancel(); runCurrent(); target=state.form
            blendFrom=blend; blendTo=blend
          } else {
            target=if(action.startsWith("inverti")) case.from else third
            blendFrom=blend; blendTo=if(action.startsWith("inverti")) 0f else .5f
            jobs += launch(clock){state.morphTo(target)}; runCurrent()
          }
          sample() // verifica anche il bordo istantaneo della ridirezione/cancellazione.
        }
        if(jobs.all { it.isCompleted } && state.liquid.quiet) break
      }
      assertTrue("non si addormenta: ${original.name}/$variant",jobs.all { it.isCompleted } && state.liquid.quiet)
      assertFalse(state.isMorphing); assertEquals(target,state.restFormNow()); assertEquals(target,state.form)
      val expected=PhysicsRenderPlan().also { buildRestPlan(it,target) }; val arrived=state.ensurePlan()
      assertEquals(expected.mode,arrived.mode); assertEquals(expected.pieceCount,arrived.pieceCount); assertEquals(expected.vertCount,arrived.vertCount)
      assertArrayEquals(expected.pieceRects.copyOf(expected.pieceCount*4),arrived.pieceRects.copyOf(arrived.pieceCount*4),.001f)
      assertArrayEquals(expected.fieldRadii.copyOf(expected.pieceCount*4),arrived.fieldRadii.copyOf(arrived.pieceCount*4),.001f)
      assertArrayEquals(expected.verts.copyOf(expected.vertCount*2),arrived.verts.copyOf(arrived.vertCount*2),.001f)
      assertSame(state.ensurePlan(),state.ensurePlan())
    }
    return Result(issues.mapValues { (k,v) -> "$v; campioni=${counts[k]}" },frames,refinedAway)
  }

  private fun freezeGeometry(p:PhysicsRenderPlan)=PhysicsRenderPlan().also { q ->
    q.mode=p.mode; q.pieceCount=p.pieceCount; q.vertCount=p.vertCount; q.bounds=p.bounds
    q.soften=p.soften; q.blendRadius=p.blendRadius; q.nominalBlend=p.nominalBlend; q.fusion=p.fusion
    p.pieceRects.copyInto(q.pieceRects); p.pieceRadii.copyInto(q.pieceRadii)
    p.fieldRadii.copyInto(q.fieldRadii); p.fieldSmooth.copyInto(q.fieldSmooth)
    p.pieceBlend.copyInto(q.pieceBlend); p.verts.copyInto(q.verts)
  }

  internal fun crossings(p:FloatArray,n:Int):Int {
    fun cross(a:Int,b:Int,c:Int)=(p[b*2]-p[a*2])*(p[c*2+1]-p[a*2+1])-(p[b*2+1]-p[a*2+1])*(p[c*2]-p[a*2])
    var count=0
    for(i in 0 until n) for(j in i+2 until n) {
      if(i==0 && j==n-1) continue
      if(cross(i,(i+1)%n,j)*cross(i,(i+1)%n,(j+1)%n)<-.001f && cross(j,(j+1)%n,i)*cross(j,(j+1)%n,(i+1)%n)<-.001f) count++
    }
    return count
  }
  // Quota d'area mancante rispetto all'inviluppo convesso: distingue una concavita' da un incrocio.
  private fun concavity(p:FloatArray,n:Int):Float {
    val points=List(n){androidx.compose.ui.geometry.Offset(p[it*2],p[it*2+1])}.distinct().sortedWith(compareBy({it.x},{it.y}))
    fun cross(a:androidx.compose.ui.geometry.Offset,b:androidx.compose.ui.geometry.Offset,c:androidx.compose.ui.geometry.Offset)=
      (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
    fun half(items:List<androidx.compose.ui.geometry.Offset>):List<androidx.compose.ui.geometry.Offset> {
      val out=mutableListOf<androidx.compose.ui.geometry.Offset>()
      for(q in items) { while(out.size>=2 && cross(out[out.size-2],out.last(),q)<=0f) out.removeAt(out.lastIndex); out+=q }
      return out.dropLast(1)
    }
    val hull=abs(signedArea(half(points)+half(points.reversed())))
    val area=abs(signedArea(List(n){androidx.compose.ui.geometry.Offset(p[it*2],p[it*2+1])}))
    return if(hull>1e-5f) 1f-area/hull else 0f
  }
  private fun fit(form:FluidForm,box:Rect,scale:Float):FluidForm {
    val f=form.frame; val rect=Rect(16+(f.left-box.left)*scale,16+(f.top-box.top)*scale,16+(f.right-box.left)*scale,16+(f.bottom-box.top)*scale)
    return when(form) {
      is FluidForm.Slab -> form.copy(frame=rect,cornerRadii=FluidCornerRadii(form.cornerRadii.topLeft*scale,form.cornerRadii.topRight*scale,form.cornerRadii.bottomRight*scale,form.cornerRadii.bottomLeft*scale))
      is FluidForm.Poly -> form.copy(frame=rect,rounding=form.rounding*scale)
      is FluidForm.Group -> form.copy(pieces=form.pieces.map { fit(it,box,scale) },blendRadius=form.blendRadius*scale)
    }
  }
}
