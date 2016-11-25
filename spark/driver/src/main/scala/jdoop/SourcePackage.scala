package jdoop

case class SourcePackage(
  val srcPkgName: String,
  val pkgDir: String,
  val srcDir: String,
  val binDir: String,
  val fqdn: String,
  val buildsBinPkgs: Set[String]) {

  private val xml =
    <source-package name={srcPkgName}>
      <pkg-dir>{pkgDir}</pkg-dir>
      <src-dir>{srcDir}</src-dir>
      <bin-dir>{binDir}</bin-dir>
      <fqdn>{fqdn}</fqdn>
      <builds>{
        for(pkg <- buildsBinPkgs)
        yield <bin-pkg>{pkg}</bin-pkg>
      }</builds>
    </source-package>

  def toXML: String = {
    // max width: 80 chars
    // indent:     2 spaces
    val printer = new scala.xml.PrettyPrinter(80, 2)
    printer.format(xml)
  }
}
