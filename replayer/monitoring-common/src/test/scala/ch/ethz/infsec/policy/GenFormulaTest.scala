package ch.ethz.infsec.policy

import ch.ethz.infsec.monitor.DataType
import ch.ethz.infsec.policy.GenFormula.{div, eql, ex, gte, i2f, once}
import org.scalatest.{FunSuite, Inside, Matchers}
import org.scalatest.enablers.Emptiness

class GenFormulaTest extends FunSuite with Matchers {
  // NOTE(JS): No idea why this is not provided automatically ...
  implicit def emptinessOfSetPrev[V]: Emptiness[Set[Pred[V]]] =
    new Emptiness[Set[Pred[V]]] {
      def isEmpty(set: Set[Pred[V]]): Boolean = set.isEmpty
    }

  test("Set of atoms") {
    val px = Pred("p", Var("x"))
    val py = Pred("p", Var("y"))
    val qy = Pred("q", Var("y"))
    val rx = Pred("r",Var("x"))
    val ry = Pred("r",Var("y"))

    True().atoms shouldBe empty
    False().atoms shouldBe empty
    px.atoms should contain only px
    Not(px).atoms should contain only px
    Or(False(), qy).atoms should contain only qy
    Or(px, px).atoms should contain only px
    Or(px, qy).atoms should contain only (px, qy)
    And(px, qy).atoms should contain only (px, qy)
    Ex("b", px).atoms should contain only px
    Ex("x", px).atoms should contain only px
    All("b", px).atoms should contain only px
    All("x", px).atoms should contain only px
    Prev(Interval(0, Some(1)), px).atoms should contain only px
    Next(Interval(0, Some(1)), px).atoms should contain only px
    Since(Interval(0, Some(1)), px, qy).atoms should contain only (px, qy)
    Trigger(Interval(0, Some(1)), px, qy).atoms should contain only (px, qy)
    Until(Interval(0, Some(1)), px, qy).atoms should contain only (px, qy)
    Release(Interval(0, Some(1)), px, qy).atoms should contain only (px, qy)
    Let(rx,px,And(qy,rx)).atoms should contain only (px, qy)
    Let(rx,And(px,Ex("y",qy)),rx).atoms should contain only (px, qy)
    Let(rx,rx,rx).atoms should contain only rx
    Let(rx,px,Let(px,rx,px)).atoms should contain only px
    Let(px,Let(px,rx,px),px).atoms should contain only rx
    Let(px,rx,And(px,py)).atoms should contain only (rx, ry)
    Aggr(Var("r"),CNT(),Var("y"),px,Seq()).atoms should contain only px


    And(px, Ex("b", Prev(Interval(0, Some(0)), Or(qy, Pred("r", Var("z"), Var("x"))))))
      .atoms should contain only (px, qy, Pred("r", Var("z"), Var("x")))
  }

  test("Atoms of a complex formula"){

    val s = Var("s");
    val id = Var("id")
    val a1 = Var("a1")
    val a2 = Var("a2")
    val a3 = Var("a3")
    val a4 = Var("a4")
    val pn = Var("pn")
    val r = Var("r")
    val dep = Var("dep")
    val c = Var("c")
    val t = Var("t")
    val ratio = Var("ratio")
    val rej = Var("rej")
    val req = Var("req")


    def chro(x:Term[String],y:Term[String],z:Term[String]):Pred[String] = Pred("chro",id,a1,x,y,z,Var("r"))

    println(
    Let[String](Pred("c_reject",id),
      ex(Seq(a1,s,a3,a4,r),And(chro(s,a3,a4),Or(eql(s,Const(5L)),eql(s,Const(19L))))),
      Let(Pred("c_accept",id),
        ex(Seq(a1,s,a3,a4,r),chro(Const(25L),a3,a4)),
        Let(Pred("c_first_person",id,pn),
          ex(Seq(a1,a3,r),chro(Const(1L),a3,pn)),
          Let(Pred("person_of_department",pn,dep),
            ex(Seq(a1,a2), once(Interval.any, Pred("pers",pn,a1,a2,dep))),
            Let(Pred("first_department",id,dep),
              Ex(pn.variable,And(Pred("c_first_person",id,pn),Pred("person_of_department",pn,dep))),
              Let(Pred("rejected_department",id,dep),
                And(Pred("c_reject",id),once(Interval.any,Pred("first_department",id,dep))),
                Let(Pred("count_rejections",c,dep),
                  Aggr(c,CNT(),id,once(Interval.any,Pred("rejected_department",id,dep)),Seq(dep)),
                  Let(Pred("count_requests",c,dep),
                    Aggr(c,CNT(),t,once(Interval.any,And(Pred("tp",t),Ex(id.variable,Pred("first_department",id,dep)))),Seq(dep)),
                    Let(Pred("result",ratio,req,rej,dep),
                      And(And(Pred("count_rejections",rej,dep),Pred("count_requests",req,dep)),eql(ratio,div(i2f(rej),i2f(req)))),
                      Let(Pred("last"),
                        Next(Interval.any,Ex(t.variable,And(Pred("ts",t),gte(t,Const(10000000000.0d))))),
                        And(Pred("last"),Pred("result",Var("a"),Var("b"),Var("c"),Var("d"))))))))))))).atoms)
  }

  test("Set of free variables") {
    val x = Var("x")
    val y = Var("y")
    val c42 = Const[String](42L)

    c42.freeVariables shouldBe empty
    Const("foo").freeVariables shouldBe empty
    x.freeVariables should contain only "x"
    Apply1(F2I(),c42).freeVariables shouldBe empty
    Apply1(F2I(),x).freeVariables should contain only "x"
    Apply1(I2F(),c42).freeVariables shouldBe empty
    Apply1(I2F(),x).freeVariables should contain only "x"
    Apply1(F2I(),Apply1(I2F(),x)).freeVariables should contain only "x"
    Apply1(F2I(),Apply2(PLUS(),c42,x)).freeVariables should contain only "x"
    Apply1(F2I(),Apply2(PLUS(),x,y)).freeVariables should contain only ("x","y")
    Apply2(MINUS(),Apply2(PLUS(),c42,x),y).freeVariables should contain only ("x","y")
    Apply2(MINUS(),Apply2(PLUS(),x,x),x).freeVariables should contain only "x"


    True().freeVariables shouldBe empty
    False().freeVariables shouldBe empty
    Pred("p").freeVariables shouldBe empty
    Pred("p", Var("x"), Var("x"), Const(2L), Var("y")).freeVariables should contain only ("x", "y")


    val px = Pred("p", x)
    val qy = Pred("q", y)
    val rx = Pred("r", x)


    Not(px).freeVariables should contain only "x"
    Or(False(), qy).freeVariables should contain only "y"
    Or(px, px).freeVariables should contain only "x"
    Or(px, qy).freeVariables should contain only ("x", "y")
    And(px, qy).freeVariables should contain only ("x", "y")
    Ex("b", px).freeVariables should contain only "x"
    Ex("x", px).freeVariables shouldBe empty
    All("b", px).freeVariables should contain only "x"
    All("x", px).freeVariables shouldBe empty
    Prev(Interval(0, Some(1)), px).freeVariables should contain only "x"
    Next(Interval(0, Some(1)), px).freeVariables should contain only "x"
    Since(Interval(0, Some(1)), px, qy).freeVariables should contain only ("x", "y")
    Trigger(Interval(0, Some(1)), px, qy).freeVariables should contain only ("x", "y")
    Until(Interval(0, Some(1)), px, qy).freeVariables should contain only ("x", "y")
    Release(Interval(0, Some(1)), px, qy).freeVariables should contain only ("x", "y")
    Let(rx,px,And(qy,rx)).freeVariables should contain only ("x", "y")
    Let(rx,And(px,Ex("y",qy)),rx).freeVariables should contain only "x"
    Let(rx,rx,rx).freeVariables should contain only "x"
    Let(rx,px,Let(px,rx,px)).freeVariables should contain only "x"
    Let(px,Let(px,rx,px),px).freeVariables should contain only "x"
    Aggr(Var("r"),CNT(),x,px,Seq()).freeVariables should contain only ("r")
    Aggr(Var("r"),CNT(),x,px,Seq(Var("x"))).freeVariables should contain only ("x","r")

    And(px, Ex("y", Prev(Interval(0, Some(0)), Or(qy, Pred("r", Var("z"), x)))))
      .freeVariables should contain only ("x", "z")
  }

  test("Types of free variables") {
    val sig = Map(("p0", 0) -> Seq.empty,
      ("p1", 1) -> Seq(DataType.INTEGRAL),
      ("p4", 4) -> Seq(DataType.STRING, DataType.STRING, DataType.INTEGRAL, DataType.FLOAT),
      ("q1", 1) -> Seq(DataType.STRING),
      ("r1", 1) -> Seq(DataType.INTEGRAL),
      ("r2", 2) -> Seq(DataType.STRING, DataType.INTEGRAL))

    val x = Var("x")
    val y = Var("y")

    True().freeVariableTypes(sig) should be (Map())
    False().freeVariableTypes(sig) should be (Map())
    Pred("p0").freeVariableTypes(sig) should be (Map())
    Pred("p4", Var("x"), Var("x"), Const(2L), Var("y")).freeVariableTypes(sig) should
      contain only ("x" -> DataType.STRING, "y" -> DataType.FLOAT)

    val px = Pred("p1", x)
    val qy = Pred("q1", y)
    val rx = Pred("r1", x)

    Not(px).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Or(False(), qy).freeVariableTypes(sig) should contain only ("y" -> DataType.STRING)
    Or(px, px).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Or(px, qy).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "y" -> DataType.STRING)
    And(px, qy).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "y" -> DataType.STRING)
    Ex("b", px).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Ex("x", px).freeVariableTypes(sig) should be (Map())
    All("b", px).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    All("x", px).freeVariableTypes(sig) should be (Map())
    Prev(Interval(0, Some(1)), px).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Next(Interval(0, Some(1)), px).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Since(Interval(0, Some(1)), px, qy).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "y" -> DataType.STRING)
    Trigger(Interval(0, Some(1)), px, qy).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "y" -> DataType.STRING)
    Until(Interval(0, Some(1)), px, qy).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "y" -> DataType.STRING)
    Release(Interval(0, Some(1)), px, qy).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "y" -> DataType.STRING)
    Let(rx,px,And(qy,rx)).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "y" -> DataType.STRING)
    Let(rx,And(px,Ex("y",qy)),rx).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Let(rx,rx,rx).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Let(rx,px,Let(px,rx,px)).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Let(px,Let(px,rx,px),px).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL)
    Aggr(Var("r"),CNT(),x,px,Seq()).freeVariableTypes(sig) should contain only ("r" -> DataType.INTEGRAL)
    Aggr(Var("r"),MAX(),y,qy,Seq()).freeVariableTypes(sig) should contain only ("r" -> DataType.STRING)
    Aggr(Var("r"),AVG(),x,px,Seq(Var("x"))).freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "r" -> DataType.FLOAT)

    And(px, Ex("y", Prev(Interval(0, Some(0)), Or(qy, Pred("r2", Var("z"), x)))))
          .freeVariableTypes(sig) should contain only ("x" -> DataType.INTEGRAL, "z" -> DataType.STRING)
  }

  test("Resolve detects free and bound variables") {
    val pyxy = Pred("p", Var("y"), Var("x"), Var("y"))

    val phi1 = GenFormula.resolve(pyxy)
    Inside.inside (phi1) { case Pred("p", Var(v1), Var(v2), Var(v3)) =>
      v1.nameHint shouldBe "y"
      v1 shouldBe 'free
      v1.freeID shouldBe 1

      v2.nameHint shouldBe "x"
      v2 shouldBe 'free
      v2.freeID shouldBe 0

      v3.nameHint shouldBe "y"
      v3 shouldBe 'free
      v3.freeID shouldBe 1

      v1 should be theSameInstanceAs v3
    }

    val phi2 = GenFormula.resolve(All("x", pyxy))
    Inside.inside (phi2) { case All(vq, Pred("p", Var(v1), Var(v2), Var(v3))) =>
      vq.nameHint shouldBe "x"
      vq should not be 'free
      vq.freeID should be < 0

      v1.nameHint shouldBe "y"
      v1 shouldBe 'free
      v1.freeID shouldBe 0

      v1 should be theSameInstanceAs v3
      vq should be theSameInstanceAs v2
    }

    val phi3 = GenFormula.resolve(Ex("y", pyxy))
    Inside.inside (phi3) { case Ex(vq, Pred("p", Var(v1), Var(v2), Var(v3))) =>
      vq.nameHint shouldBe "y"
      vq should not be 'free
      vq.freeID should be < 0

      v2.nameHint shouldBe "x"
      v2 shouldBe 'free
      v2.freeID shouldBe 0

      vq should be theSameInstanceAs v1
      v1 should be theSameInstanceAs v3
    }

    val aggpyxy = Aggr(Var("r"), SUM(), Var("x"),pyxy, Seq())
    val phi4 = GenFormula.resolve(aggpyxy)
    Inside.inside(phi4){ case Aggr(Var(r), SUM(), Var(x), Pred("p", Var(v1), Var(v2), Var(v3)), Seq()) =>
      r.nameHint shouldBe "r"
      r shouldBe 'free
      r.freeID shouldBe 0

      x.nameHint shouldBe "x"
      x should not be 'free
      x.freeID should be < 0

      v1 should not be 'free
      v2 should not be 'free
      v3 should not be 'free

      x should be theSameInstanceAs v2
      v1 should be theSameInstanceAs v3
    }

    val aggpyxygx = Aggr(Var("r"), SUM(), Var("x"),pyxy, Seq(Var("x")))
    val phi5 = GenFormula.resolve(aggpyxygx)
    Inside.inside(phi5){ case Aggr(Var(r), SUM(), Var(x), Pred("p", Var(v1), Var(v2), Var(v3)), Seq(Var(g))) =>
      r.nameHint shouldBe "r"
      r shouldBe 'free
      r.freeID shouldBe 0

      x.nameHint shouldBe "x"
      x shouldBe 'free
      x.freeID shouldBe 1

      v1 should not be 'free
      v2 shouldBe 'free
      v3 should not be 'free

      x should be theSameInstanceAs v2
      x should be theSameInstanceAs g
      v1 should be theSameInstanceAs v3
    }

    val letqx = Let(Pred("q",Var("y")),Ex("x", pyxy),Pred("q",Var("x")))
    val phi6 = GenFormula.resolve(letqx)
    Inside.inside(phi6){ case Let(Pred("q",Var(v1)),Ex(v2, Pred("p", Var(v3), Var(v4), Var(v5))),Pred("q",Var(v6))) =>

      v6.nameHint shouldBe "x"
      v6 shouldBe 'free
      v6.freeID shouldBe 0

      v1.nameHint shouldBe "y"
      v1 should not be 'free
      v1.freeID should be < 0

      v2.nameHint shouldBe "x"
      v2 should not be 'free
      v2.freeID should be < 0

      v2 should be theSameInstanceAs v4
      v3 should be theSameInstanceAs v5
      v1 should be theSameInstanceAs v3
    }
  }

  test("Printing after resolve") {
    def roundTrip(phi: GenFormula[String]): GenFormula[String] = GenFormula.print(GenFormula.resolve(phi))

    val x = Var("x")
    val y = Var("y")
    val px = Pred("p", x)
    val py = Pred("p", y)

    roundTrip(True()) shouldBe True()
    roundTrip(False()) shouldBe False()
    roundTrip(Not(px)) shouldBe Not(px)
    roundTrip(Or(False(), px)) shouldBe Or(False(), px)
    roundTrip(Or(px, px)) shouldBe Or(px, px)
    roundTrip(Or(px, py)) shouldBe Or(px, py)
    roundTrip(And(px, py)) shouldBe And(px, py)
    roundTrip(Ex("u", px)) shouldBe Ex("u", px)
    roundTrip(Ex("x", px)) shouldBe Ex("x", px)
    roundTrip(All("u", px)) shouldBe All("u", px)
    roundTrip(All("x", px)) shouldBe All("x", px)
    roundTrip(Prev(Interval.any, px)) shouldBe Prev(Interval.any, px)
    roundTrip(Next(Interval.any, px)) shouldBe Next(Interval.any, px)
    roundTrip(Since(Interval.any, px, py)) shouldBe Since(Interval.any, px, py)
    roundTrip(Trigger(Interval.any, px, py)) shouldBe Trigger(Interval.any, px, py)
    roundTrip(Until(Interval.any, px, py)) shouldBe Until(Interval.any, px, py)
    roundTrip(Release(Interval.any, px, py)) shouldBe Release(Interval.any, px, py)

    roundTrip(All("x", Ex("x", px))) shouldBe All("x", Ex("x_1", Pred("p", Var("x_1"))))
    roundTrip(All("x", Ex("y", px))) shouldBe All("x", Ex("y", px))
    roundTrip(All("x", Ex("y", py))) shouldBe All("x", Ex("y", py))
    roundTrip(All("x", And(Ex("x", px), px))) shouldBe All("x", And(Ex("x_1", Pred("p", Var("x_1"))), px))
    roundTrip(Ex("x", Ex("x", All("x", px)))) shouldBe Ex("x", Ex("x_1", All("x_2", Pred("p", Var("x_2")))))

    roundTrip(And(px, Ex("x", px))) shouldBe And(px, Ex("x_1", Pred("p", Var("x_1"))))
    roundTrip(Or(All("y", And(py, px)), py)) shouldBe Or(All("y_1", And(Pred("p", Var("y_1")), px)), py)

    roundTrip(Aggr(Var("r"), SUM(), Var("x"), px, Seq(Var("x")))) shouldBe Aggr(Var("r"), SUM(), Var("x"), px, Seq(Var("x")))
    roundTrip(Aggr(Var("r"), CNT(), Var("x"), px, Seq())) shouldBe Aggr(Var("r"), CNT(), Var("x"), px, Seq())
  }

  test("Checking valid intervals") {
    Interval.any.check shouldBe empty
    Interval(1, None).check shouldBe empty
    Interval(321, None).check shouldBe empty
    Interval(0, Some(1)).check shouldBe empty
    Interval(1, Some(100)).check shouldBe empty
    Interval(321, Some(322)).check shouldBe empty
  }

  test("Checking invalid intervals") {
    Interval(-1, None).check should not be empty
    Interval(-1, Some(1)).check should not be empty
    Interval(0, Some(0)).check should not be empty
    Interval(1, Some(0)).check should not be empty
    Interval(321, Some(123)).check should not be empty
    Interval(-321, Some(-322)).check should not be empty
  }

  test("Checking valid formulas") {
    val x = Var("x")
    val y = Var("y")
    val px = Pred("p", x)
    val py = Pred("p", y)
    val temporal = Since(Interval.any, px, py)

    True().intervalCheck shouldBe empty
    False().intervalCheck shouldBe empty
    Not(px).intervalCheck shouldBe empty
    Or(False(), px).intervalCheck shouldBe empty
    Or(px, px).intervalCheck shouldBe empty
    Or(px, py).intervalCheck shouldBe empty
    And(px, py).intervalCheck shouldBe empty
    Ex("u", px).intervalCheck shouldBe empty
    Ex("x", px).intervalCheck shouldBe empty
    All("u", px).intervalCheck shouldBe empty
    All("x", px).intervalCheck shouldBe empty
    Prev(Interval.any, px).intervalCheck shouldBe empty
    Next(Interval.any, px).intervalCheck shouldBe empty
    Since(Interval.any, px, py).intervalCheck shouldBe empty
    Trigger(Interval.any, px, py).intervalCheck shouldBe empty
    Until(Interval.any, px, py).intervalCheck shouldBe empty
    Release(Interval.any, px, py).intervalCheck shouldBe empty

    Prev(Interval(1, None), px).intervalCheck shouldBe empty
    Prev(Interval(0, Some(1)), px).intervalCheck shouldBe empty
    Prev(Interval(1, Some(100)), px).intervalCheck shouldBe empty

    And(Pred("P", Var("x")), Ex("b", Prev(Interval.any, Or(Pred("Q"), Pred("r", Var("z"), Var("x"))))))
      .intervalCheck shouldBe empty

    Aggr(Var("r"), SUM(), Var("x"), px, Seq(Var("x"))).intervalCheck shouldBe empty
    Aggr(Var("r"), SUM(), Var("x"), temporal, Seq(Var("x"))).intervalCheck shouldBe empty

    Let(Pred("q",Var("y")),Ex("x", And(px,py)),Pred("q",Var("x"))).intervalCheck shouldBe empty
    Let(Pred("q",Var("y")),Ex("x", temporal),Pred("q",Var("x"))).intervalCheck shouldBe empty


    /*
    All("x", Ex("x", px)).check shouldBe empty
    All("x", Ex("y", px)).check shouldBe empty
    All("x", Ex("y", py)).check shouldBe empty

    And(Pred("p", x), Ex("x", Pred("p", x))).check shouldBe
      And(Pred("p", x0), Ex("x", Pred("p", Bound(0, "x"))))
    Or(All("y", And(py, px)), Pred("p", y)).check shouldBe
      Or(All("y", And(Pred("p", Bound(0, "y")), Pred("p", x0))), Pred("p", y1))
      */
  }

  test("Checking invalid formulas") {
    val x = Var("x")
    val y = Var("y")
    val px = Pred("p", x)
    val py = Pred("p", y)
    val bad1 = Interval(-1, Some(1))
    val bad2 = Interval(5, Some(4))
    val temporal1 = Since(bad1, px, py)
    val temporal2 = Since(bad2, px, py)

    Prev(bad1, True()).intervalCheck should not be empty
    Prev(bad2, True()).intervalCheck should not be empty
    Next(bad1, True()).intervalCheck should not be empty
    Next(bad2, True()).intervalCheck should not be empty
    Since(bad1, True(), True()).intervalCheck should not be empty
    Trigger(bad1, True(), True()).intervalCheck should not be empty
    Until(bad1, True(), True()).intervalCheck should not be empty
    Release(bad1, True(), True()).intervalCheck should not be empty

    val prevBad: GenFormula[String] = Prev(bad1, True())
    val prevGood: GenFormula[String] = Prev(Interval.any, True())

    Or(prevBad, prevGood).intervalCheck should not be empty
    Or(prevGood, prevBad).intervalCheck should not be empty
    Or(prevBad, prevBad).intervalCheck should not be empty
    And(prevBad, prevGood).intervalCheck should not be empty
    And(prevGood, prevBad).intervalCheck should not be empty
    And(prevBad, prevBad).intervalCheck should not be empty
    All("x", prevBad).intervalCheck should not be empty
    Ex("x", prevBad).intervalCheck should not be empty
    Prev(Interval.any, prevBad).intervalCheck should not be empty
    Next(Interval.any, prevBad).intervalCheck should not be empty
    Since(Interval.any, prevBad, True()).intervalCheck should not be empty
    Trigger(Interval.any, prevBad, True()).intervalCheck should not be empty
    Until(Interval.any, True(), prevBad).intervalCheck should not be empty
    Release(Interval.any, True(), prevBad).intervalCheck should not be empty

    And(Pred("P", Var("x")), Ex("b", Prev(bad2, Or(Pred("Q"), Pred("r", Var("z"), Var("x"))))))
      .intervalCheck should not be empty

    Aggr(Var("r"), SUM(), Var("x"), temporal1, Seq(Var("x"))).intervalCheck should not be empty
    Aggr(Var("r"), SUM(), Var("x"), temporal2, Seq(Var("x"))).intervalCheck should not be empty

    Let(Pred("q",Var("y")),Ex("x", temporal1),Pred("q",Var("x"))).intervalCheck should not be empty
    Let(Pred("q",Var("y")),Ex("x", temporal2),Pred("q",Var("x"))).intervalCheck should not be empty
  }
}
