package io.adamnfish.pcb

case class Arguments(
  inputDir: String,
  outputDir: String,
  dryRun: Boolean,
)

case class Filenames(
  dirs: String,
  filename: String,
)
