package endpoints

trait AssetAlg extends EndpointAlg {

  type AssetInfo
  type Asset

  def assetSegments: Path[AssetInfo]

  def assetsEndpoint(url: Url[AssetInfo]): Endpoint[AssetInfo, Asset]

  def digests: Map[String, String] // FIXME Move as a method parameter

}