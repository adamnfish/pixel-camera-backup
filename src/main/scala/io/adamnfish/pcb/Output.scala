package io.adamnfish.pcb

trait Output:
  def stdout(line: String): Unit
  def stderr(line: String): Unit

  def stdoutln(line: String): Unit = stdout(line + "\n")
  def stderrln(line: String): Unit = stderr(line + "\n")

object ConsoleOutput extends Output:
  def stdout(line: String): Unit = System.out.print(line)
  def stderr(line: String): Unit = System.err.print(line)

// Test functionality

class TestOutput extends Output:
  private val output = scala.collection.mutable.Buffer[OutputLine]()
  def stdout(line: String): Unit = output += OutputLine.Stdout(line)
  def stderr(line: String): Unit = output += OutputLine.Stderr(line)

  def getAllOutput: List[OutputLine] = output.toList
  def getStdout: List[String] = output.collect { case OutputLine.Stdout(line) =>
    line
  }.toList
  def getStderr: List[String] = output.collect { case OutputLine.Stderr(line) =>
    line
  }.toList
  def clear(): Unit = output.clear()

enum OutputLine:
  case Stdout(content: String) extends OutputLine
  case Stderr(content: String) extends OutputLine
object OutputLine:
  def out(s: String): OutputLine = Stdout(s)
  def err(s: String): OutputLine = Stderr(s)
  def outln(s: String): OutputLine = Stdout(s + "\n")
  def errln(s: String): OutputLine = Stderr(s + "\n")
