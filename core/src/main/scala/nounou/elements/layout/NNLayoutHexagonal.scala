package nounou.elements.layout

import javafx.scene.shape.Rectangle

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**A hexagonal detector layout, useful for hexagonally packed
  * imaging arrays or ECoG arrays.
  *
  */

class NNLayoutHexagonal extends NNLayoutSpatial {

  /** Main variable for this layout.
    * This {{{Array[Array[Int]]}}} should contain the channels laid out in tilted hexagonalspace,
    * with the first dimension being the x coordinate, and starting from the upper left corner.
    */
  protected var layoutArray: Array[Array[Int]] = null

  def this(layoutArray: Array[Array[Int]]) {
    this
    this.layoutArray = layoutArray
    initialize()
  }
  def this(layoutArray: Array[Array[Int]], channelRadius: Double, channelDistance: Double) {
    this
    this.layoutArray = layoutArray
    this.channelRadius = channelRadius
    this.channelDistance = channelDistance
    initialize()
  }

  @transient protected var layoutLookupCache: mutable.HashMap[Int, (Int, Int)] = null
  @transient private val vertFactor = math.sqrt(3d)/2d
  @transient private var minLayArrX = Integer.MAX_VALUE
  @transient private var maxLayArrX = Integer.MIN_VALUE
  @transient private var minLayArrY = Integer.MAX_VALUE
  @transient private var maxLayArrY = Integer.MIN_VALUE

  /**This initialization is done here, and not in the constructor automatically,
    * to allow serialization with gson. Gson requires classes to be
    * initializable with a no-argument constructor.
   */
  override def initialize(): Unit = {
    loggerRequire(!initialized, "NNDataLayoutHexagonal should only be initialized once!")

    val allChannels = new ArrayBuffer[Boolean]()
    val layoutLookupTemp = new mutable.HashMap[Int, (Int, Int)]()

    def updateMaxMin(x: Int, y: Int): Unit = {
      if( x < minLayArrX ) minLayArrX = x
      if( x > maxLayArrX ) maxLayArrX = x
      if( y < minLayArrY ) minLayArrY = y
      if( y > maxLayArrY ) maxLayArrY = y
    }
    def writeNewChannelCache(newChannel: Int, coordinates: (Int, Int)): Unit = {
      //Expand allChannels if new channel is beyond extent
      if( newChannel >= allChannels.length ) allChannels ++= new Array[Boolean](newChannel - allChannels.length + 1)
      loggerRequire( !allChannels(newChannel), s"specified layout contains duplicate channel entry for: $newChannel!" )
      allChannels(newChannel) = true
      //Add channel coordinates to the layout lookup
      layoutLookupTemp  += newChannel -> coordinates
    }

    loggerRequire( layoutArray != null, "layoutArray must be specified in order to initialize this object!" )
    for( layArrY <- 0 until layoutArray.length ){
      val layArrRow = layoutArray(layArrY)
      if( layArrRow != null && layArrRow.length != 0) {
        // updateMaxMin(0, layArrY)  }
        //      else
        for (layArrX <- 0 until layArrRow.length) {
          val chVal = layoutArray(layArrY)(layArrX)
          if (chVal >= 0) {
            writeNewChannelCache(chVal, (layArrX, layArrY))
            updateMaxMin(layArrX, layArrY)
          }
        }
      }
    }
    loggerRequire( allChannels.forall( p => p ),
      s"some channels were not represented in your layout: ${
        allChannels.zipWithIndex.filter( (p:(Boolean, Int)) => !p._1 ).map( (p:(Boolean, Int)) => p._2 ).toList
      }" )
    layoutLookupCache = layoutLookupTemp
    channelCountCache = allChannels.length
    fieldCache = new Rectangle( (minLayArrX-0.5d) * channelDistance, (minLayArrY-0.5d) * channelDistance * vertFactor,
                                (maxLayArrX - minLayArrX + 1d) * channelDistance,
                                (maxLayArrY - minLayArrY + 1d) * channelDistance * vertFactor)
    initialized = true

  }

  def getChannelHexCoordinates(ch: Int): (Int, Int) = layoutLookupCache(ch)

  override def getChannelCoordinatesImpl(ch: Int): Array[Double] = {
   getCoordinatesFromHexCoordinates( getChannelHexCoordinates(ch) )
  }

  private def getCoordinatesFromHexCoordinates( hexCoord: (Int, Int) ): Array[Double] = {
    Array(
      (hexCoord._1 + hexCoord._2 * 0.5) * channelDistance ,
      (layoutArray.length - hexCoord._2) * channelDistance * vertFactor
    )
  }

//  override def coordinatesToChannelImpl(x: Double, y: Double): Int = {
//    val xCoord = round( x/channelDistance ).toInt
//    val yCoord = round( y/(channelDistance * vertFactor) ).toInt
//    //ToDo 3: make chopping function in breeze
//    val xRealCoord =
//      if( xCoord < minLayArrX ) minLayArrX
//      else if( xCoord > maxLayArrX ) maxLayArrX
//      else xCoord
//    val yRealCoord =
//      if( yCoord < minLayArrY ) minLayArrY
//      else if( yCoord > maxLayArrY ) maxLayArrY
//      else yCoord
//
//    layoutArray(yRealCoord)(xRealCoord)
//  }

}
