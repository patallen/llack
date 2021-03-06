package org.llack.mid

import org.testng.annotations.{Test, BeforeMethod}

import org.scalatest.testng.TestNGSuite
import org.scalatest.prop.Checkers
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalatest._

import org.llack.vm._

class MidTest extends TestNGSuite with Checkers {

  @Test
  def testCompile: Unit = {
    val i32 = IntegerType(32)

    val dict = new Dictionary

    def addPrimitive(dict: Dictionary, wid: GenericWord.Id, specializer: PartialFunction[GenericWord.Args, InstructionWriter]): Unit = {
      dict(wid) = new GenericWord {
	def extern = false
	def specialize(args: GenericWord.Args) = {
	  if (specializer.isDefinedAt(args)) specializer(args)
	  else error(wid + ": incorrect args: " + args)
	}
      }
    }

    addPrimitive(dict, "dup", {
      case (t:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(ShuffleInst(List(t), List(0, 0)))
      }
    })
    addPrimitive(dict, "swap", {
      case (t0:Type)::(t1:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(ShuffleInst(List(t0, t1), List(1, 0)))
      }
    })
    addPrimitive(dict, "rot", {
      case (t0:Type)::(t1:Type)::(t2:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(ShuffleInst(List(t0, t1, t2), List(1, 2, 0)))
      }
    })
    addPrimitive(dict, "drop", {
      case (t:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(ShuffleInst(List(t), List()))
      }
    })
    addPrimitive(dict, "select", {
      case (t:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(SelectInst(t))
      }
    })
    addPrimitive(dict, "tocont", {
      case Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(ToContInst)
      }
    })
    addPrimitive(dict, "fromcont", {
      case Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(FromContInst)
      }
    })
    addPrimitive(dict, "add", {
      case (t:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(AddInst(t))
      }
    })
    addPrimitive(dict, "sub", {
      case (t:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(SubInst(t))
      }
    })
    addPrimitive(dict, "mul", {
      case (t:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(MulInst(t))
      }
    })
    addPrimitive(dict, "icmp", {
      case (c:IntegerCondition)::(t:Type)::Nil => new InstructionWriter {
        def write(buf: InstructionBuffer) = buf.append(ICmpInst(c, t))
      }
    })

    Term.addToDictionary(dict, "apply", false, QuotationTerm(List(
      WordTerm("tocont", Nil)
    )))

    Term.addToDictionary(dict, "if", false, QuotationTerm(List(
      WordTerm("select", List(ConstantArgument(QuotationType))),
      WordTerm("tocont", Nil)
    )))


    Term.addToDictionary(dict, "factorial", true, QuotationTerm(List(
      LiteralTerm(1, VariableArgument(0)),
      WordTerm("factorial_accum", List(VariableArgument(0)))
    )))
    
    Term.addToDictionary(dict, "factorial_accum", false, QuotationTerm(List(
      WordTerm("swap", List(VariableArgument(0), VariableArgument(0))),
      WordTerm("dup", List(VariableArgument(0))),
      LiteralTerm(1, VariableArgument(0)),
      WordTerm("icmp", List(ConstantArgument(SLECond), VariableArgument(0))),
      QuotationTerm(List(
	WordTerm("drop", List(VariableArgument(0)))
      )),
      QuotationTerm(List(
	WordTerm("dup", List(VariableArgument(0))),
	LiteralTerm(1, VariableArgument(0)),
	WordTerm("sub", List(VariableArgument(0))),
	WordTerm("swap", List(VariableArgument(0), VariableArgument(0))),
	WordTerm("rot", List(VariableArgument(0), VariableArgument(0), VariableArgument(0))),
	WordTerm("mul", List(VariableArgument(0))),
	WordTerm("factorial_accum", List(VariableArgument(0))),
      )),
      WordTerm("if", Nil)
    )))

    // (A (A -> A) b -> A)
    Term.addToDictionary(dict, "times", true, QuotationTerm(List(
      // quot count
      WordTerm("dup", List(VariableArgument(0))), // quot count count
      LiteralTerm(1, VariableArgument(0)), // quot count count 1
      WordTerm("icmp", List(ConstantArgument(SLECond), VariableArgument(0))), // quot count bool
      QuotationTerm(List(
	// quot count
	WordTerm("drop", List(VariableArgument(0))), // quot
	WordTerm("drop", List(ConstantArgument(QuotationType))) //
      )),
      QuotationTerm(List(
	// quot count
	LiteralTerm(1, VariableArgument(0)), // quot count1
	WordTerm("sub", List(VariableArgument(0))), // quot count1
	WordTerm("swap", List(ConstantArgument(QuotationType), VariableArgument(0))), // count1 quot
	WordTerm("dup", List(ConstantArgument(QuotationType))), // count1 quot quot
	WordTerm("apply", Nil), // count1 quot quot
	WordTerm("swap", List(VariableArgument(0), ConstantArgument(QuotationType))), // quot count1
	WordTerm("times", List(VariableArgument(0))) // [recursive]
      )),
      WordTerm("if", Nil)
    )))

    Term.addToDictionary(dict, "main", true, QuotationTerm(List(
      QuotationTerm(List(
	LiteralTerm(5, ConstantArgument(i32)),
	WordTerm("factorial", List(ConstantArgument(i32))),
	WordTerm("drop", List(ConstantArgument(i32)))	
      )),
      LiteralTerm(3, ConstantArgument(i32)),
      WordTerm("times", List(ConstantArgument(i32)))
    )))

    val module = new Module
    module.moduleIdentifier = Some("midmod")
    module.dataLayout = Some("e-p:32:32:32-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:32:64-f32:32:32-f64:32:64-v64:64:64-v128:128:128-a0:0:64-f80:128:128")
    module.targetTriple = Some("i386-apple-darwin8")

    Compiler.compile("main", Nil, dict, module)    
    println(Disassembler.disassemble(module))
    println(CPlusPlusEmitter.emit(module))
  }
}
