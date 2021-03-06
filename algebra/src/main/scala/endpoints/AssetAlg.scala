package endpoints

trait AssetAlg extends EndpointAlg {

  /** An HTTP request to retrieve an asset */
  type AssetRequest
  /** The path of the asset */
  type AssetPath
  /** An HTTP response containing an asset */
  type AssetResponse

  /**
    * A [[Path]] that extracts an [[AssetPath]] from all the path segments.
    *
    * Consider the following definition:
    * {{{
    *   val assets = assetsEndpoint(get(path / "assets" / assetsSegments))
    * }}}
    *
    * Then, here is how the following requests are decoded:
    * - `/assets/foo` => `foo`
    * - `/assets/foo/bar` => `foo/bar`
    */
  def assetSegments: Path[AssetPath]

  /**
    * @param url URL description
    * @return An HTTP endpoint serving assets
    */
  def assetsEndpoint(url: Url[AssetPath]): Endpoint[AssetRequest, AssetResponse]

  /** The digests of the assets */
  def digests: Map[String, String] // FIXME Move as a method parameter

}
