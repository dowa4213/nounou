package nounou.elements.data

import breeze.linalg.{DenseVector => DV}
import nounou._
import nounou.elements.NNElement
import nounou.elements.traits.layout.NNLayout
import nounou.elements.traits.{NNTimingElement, NNScalingElement, NNChannelsElement}
import nounou.ranges.{NNRange, NNRangeSpecifier, NNRangeValid}

/** Base trait for data encoded as Int arrays, this is the main data element for an experiment,
  * whether it be electrophysiolgical or high-sampling-rate imaging.
  *
  * This object is mutable, to allow inheritance by [[nounou.elements.data.filters.NNDataFilter]].
  * For that class, output results may change, depending upon _parent changes.
  * Each trace of data must share the following variables:
  * sampling, start, length, xBits, absGain, absOffset, absUnit
  */
trait NNData extends NNElement
  with NNChannelsElement with NNScalingElement with NNTimingElement
  with NNDataSpikeReader {

  override def toStringImpl() = s"${channelCount} ch, ${timing().segmentCount} seg, fs=${timing().sampleRate}, "
  override def toStringFullImpl() = ""


  /** Provides a textual representation of the child hierarchy starting from this data object.
    * If multiple NNDataFilter objects (e.g. an [[nounou.elements.data.filters.NNDataFilterFIR]] object)
    * are chained after this data, this method will show the chained objects and their tree hierarchy.
    * @return
    */
  final def toStringChain(): String = {
    toString() + (if(getChildren.size!=0) {
      getChildren.map(p => "\n\\ " + p.toStringChain).reduce( _ + _ ).split("\n").flatMap( "\n     " + _ ).mkString.drop(1)
    } else {
      ""
    })
  }

  // <editor-fold defaultstate="collapsed" desc=" DataSource and child related ">

  private val _children = scala.collection.mutable.Set[NNData]()
  /**'''[NNData: data source]''' Adds a new child to this data source, which will be notified for changes.
    * Be sure to clear children if the children are to be garbage collected.*/
  final def setChild(x: NNData): Unit = _children.+=(x)
  /**'''[NNData: data source]''' Adds new children to this data source, which will be notified for changes.
    * Be sure to clear children if the children are to be garbage collected.*/
  final def setChildren(xs: TraversableOnce[NNData]): Unit = xs.map( setChild(_) )
  /**'''[NNData: data source]''' Children of this data source which should be notified upon changes.*/
  final def getChildren() = _children
  /**'''[NNData: data source]''' Clear all children of this data source.*/
  final def clearChildren(): Unit = _children.clear()
  /**'''[NNData: data source]''' Clear specified child of this data source.*/
  final def clearChild(x: NNData): Unit = _children.-=(x)
  /**'''[NNData: data source]''' Clear specified children of this data source.*/
  final def clearChildren(xs: TraversableOnce[NNData]): Unit = xs.map( clearChild(_) )

  /** '''__SHOULD OVERRIDE__''' especially by buffering functions
    * and display functions which have an internal state to update.
    * Always remember to start with super.changedData(), in order to
    * map to all children.
    */
  def changedData(): Unit = _children.map(_.changedData())
  /** '''__SHOULD OVERRIDE__''' especially by buffering functions
    * and display functions which have an internal state to update.
    * Always remember to start with super.changedData(Int), in order to
    * map to all children.
    */
  def changedData(channel: Int): Unit = _children.map(_.changedData(channel))
  /** '''__SHOULD OVERRIDE__''' especially by buffering functions
    * and display functions which have an internal state to update.
    * Always remember to start with super.changedData(Array[Int]), in order to
    * map to all children.
    */
  def changedData(channels: Array[Int]): Unit = _children.map(_.changedData(channels))
  /** '''__SHOULD OVERRIDE__''' especially by buffering functions
    * and display functions which have an internal state to update.
    * Covers sampleRate, segmentLengths, segmentEndTSs, segmentStartTSs, segmentCount
    * Always remember to start with super.changedTiming(), in order to
    * map to all children.
    */
  def changedTiming(): Unit = _children.map(_.changedTiming())
  /** '''__SHOULD OVERRIDE__'''  especially by buffering functions
    * and display functions which have an internal state to update.
    * Always remember to start with super.changedLayout(), in order to
    * map to all children.
    */
  def changedLayout(): Unit = _children.map(_.changedLayout())

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc=" NNLayout, channel count ">

  private var varLayout: NNLayout = null
  final def layout(): NNLayout = getLayout()
  def getLayout(): NNLayout = varLayout
  def setLayout(layout: NNLayout): Unit = {
    loggerRequire( layout.channelCount == this.channelCount(),
      s"Channel count ${layout.channelCount} of new layout does not match channel count ${this.channelCount()} for ${this.getClass.toString}" )
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc=" readXXX ">

  //<editor-fold defaultstate="collapsed" desc="reading a point">

  /** Read a single point from the data, in internal integer scaling, after checking values.
    * Implement via readPointImpl. Prefer
    * [[nounou.elements.data.NNData.readTraceIntDV(channel:Int,range* readTraceDV]]
    * and readFrame()
    * when possible, as these will avoid repeated function calling overhead.
    */
  def readPointInt(channel: Int, frame: Int, segment: Int /*optSegment: OptSegment*/): Int = {
    val realSegment = timing.getRealSegment(segment)
    loggerRequire( timing.isRealisticFrsg(frame, realSegment), s"Unrealistic frame/segment: ${frame}/${segment})" )
    loggerRequire(isValidChannel(channel), s"Invalid channel: " + channel.toString)

    if( timing.isValidFrsg(frame, realSegment) ) readPointIntImpl(channel, frame, realSegment)
    else 0
  }
  final def readPointInt(channel: Int, frame: Int): Int = readPointInt(channel, frame, -1)

  // <editor-fold defaultstate="collapsed" desc=" convenience readPoint variations ">

  /** [[nounou.elements.data.NNData.readPointInt(channel:Int,frame:Int,segment:Int)* readPoint]] but in physical units.
    */
  final def readPoint(channel: Int, frame: Int, segment: Int): Double = scale.convertIntToAbsolute( readPointInt(channel, frame, segment) )
  //final def readPointAbs(channel: Int, frame: Int, optSegment: OptSegment): Double = convertINTtoABS( readPoint(channel, frame, optSegment) )
  /** [[nounou.elements.data.NNData.readPointInt(channel:Int,frame:Int)* readPoint]]
    * but in physical units.
    */
  final def readPoint(channel: Int, frame: Int): Double = readPoint(channel, frame, -1)//OptSegmentAutomatic)

  // </editor-fold>

  //</editor-fold>

  /** '''__MUST OVERRIDE__''' Read a single point from the data, in internal integer scaling.
    * Assumes that channel, frame, and segment are all valid and within range.
    */
  @deprecated
  def readPointIntImpl(channel: Int, frame: Int, segment: Int): Int
  def readPointImpl(channel: Int, frame: Int, segment: Int): Double = ???

  //<editor-fold defaultstate="collapsed" desc="reading a trace">

  /** '''__CAN OVERRIDE__''' Read a single trace from the data, in internal integer scaling.
    */
  @deprecated
  def readTraceIntDV(channel: Int, range: NNRangeSpecifier): DV[Int] = {

    loggerRequire(isValidChannel(channel), "Invalid channel: " + channel.toString)

    range match {
      case ran: NNRangeValid => readTraceIntDVImpl(channel, ran)
      case _ => {
        loggerRequire(timing.isRealisticRange(range), "Unrealistic frame/segment: " + range.toString)
        val preValidPost = range.getRangeValidPrePost(this)
        readTraceIntDVPVPImpl(channel, preValidPost)
      }
    }

  }
  /** '''__CAN OVERRIDE__''' by default, calls
    * [[nounou.elements.data.NNData.readTraceIntDV(channel:Int,range* readTraceDV ]]
    */
  @deprecated
  def readTraceIntDV(channels: Array[Int], range: NNRangeSpecifier): Array[DV[Int]] = {
    val preValidPost = range.getRangeValidPrePost(this)
    channels.map( readTraceIntDVPVPImpl(_, preValidPost) )
  }

  @deprecated
  private final def readTraceIntDVPVPImpl(channel: Int, preValidPost: (Int, NNRangeValid, Int)): DV[Int] = {
    val validTrace = readTraceIntDVImpl(channel, preValidPost._2)
    preValidPost match {
      case (0, rfv, 0) => validTrace
      case (pre, rfv, 0) => DV.vertcat(DV.zeros[Int](pre), validTrace)
      case (0, rfv, post) => DV.vertcat(validTrace, DV.zeros[Int](post))
      case (pre, rfv, post) => DV.vertcat(DV.zeros[Int](pre), validTrace, DV.zeros[Int](post))
    }
  }
  private final def readTraceDVPVPImpl(channel: Int, preValidPost: (Int, NNRangeValid, Int)): DV[Double] = {
    val validTrace = readTraceDVImpl(channel, preValidPost._2)
    preValidPost match {
      case (0, rfv, 0) => validTrace
      case (pre, rfv, 0) => DV.vertcat(DV.zeros[Double](pre), validTrace)
      case (0, rfv, post) => DV.vertcat(validTrace, DV.zeros[Double](post))
      case (pre, rfv, post) => DV.vertcat(DV.zeros[Double](pre), validTrace, DV.zeros[Double](post))
    }
  }


  // <editor-fold defaultstate="collapsed" desc=" convenience readTrace variations ">

  /**Internal implementation for [[nounou.elements.data.NNData.readTraceInt(channel:Int)* readTrace]]
    * to return a breeze.linalg.DenseVector.
    */
  @deprecated
  protected[nounou] final def readTraceIntDV(channel: Int): DV[Int] = readTraceIntDV(channel, NN.NNRangeAll())

//  final def readTraceDV(channel: Int): DV[Double]
//    = scale.convertIntToAbsolute(readTraceIntDV(channel))
  /** Read a single trace in absolute unit scaling (as recorded).*/
  protected[nounou] final def readTraceDV(channel: Int, range: NNRangeSpecifier = NN.NNRangeAll()): DV[Double]
    = scale.convertIntToAbsolute(readTraceIntDV(channel, range))
  /** Read multiple traces in absolute unit scaling (as recorded).*/
  protected[nounou] final def readTraceDV(channels: Array[Int], range: NNRangeSpecifier): Array[DV[Double]]
    = readTraceIntDV(channels, range).map( scale.convertIntToAbsolute(_) )


  /** Read a single trace (whole range) in internal integer scaling.
    * This should only be used for data with only 1 segment.
    */
  @deprecated
  protected[nounou] final def readTraceInt(channel: Int) =
                          readTraceIntDV(channel).toArray
  @deprecated
  protected[nounou] final def readTraceInt(channel: Int, range: NNRangeSpecifier) =
                          readTraceIntDV(channel, range).toArray
  @deprecated
  protected[nounou] final def readTraceInt(channels: Array[Int], range: NNRangeSpecifier): Array[Array[Int]] =
                          readTraceIntDV(channels, range).map(_.toArray)
  @deprecated
  protected[nounou] final def readTraceInt(channel: Int, range: Array[Int]): Array[Int] =
                          readTraceInt(channel, NN.NNRange(range))
  @deprecated
  protected[nounou] final def readTraceInt(channel: Int, range: Array[Int], segment: Int): Array[Int] =
                          readTraceInt(channel, NN.NNRange(range, segment))
//  final def readTraceA(channels: Array[Int], range: Array[Int]): Array[Array[Int]] =           readTraceA(channels, convertARRtoRANGE(range))

  /** Read a single trace in absolute unit scaling (as recorded).
    * This should only be used for data with only 1 segment.
    */
  final def readTrace(channel: Int): Array[Double] = readTraceDV(channel).toArray
  final def readTrace(channel: Int, range: NNRangeSpecifier): Array[Double] =
      readTraceDV(channel, range).toArray
//  final def readTraceAbsA(channels: Array[Int], range: SampleRangeSpecifier): Array[Array[Double]] =         readTraceAbs(channels, range).map(_.toArray)
  final def readTrace(channel: Int, range: Array[Int], segment: Int): Array[Double] =
      readTrace(channel, NNRange.convertArrayToSampleRange(range, segment) )
  final def readTrace(channel: Int, range: Array[Int]): Array[Double] =
      readTrace(channel, NNRange.convertArrayToSampleRange(range, -1) )
//  final def readTraceAbsA(channels: Array[Int], range: Array[Int]): Array[Array[Double]] =         readTraceAbsA(channels, convertARRtoRANGE(range))


  //</editor-fold>

  /** CAN OVERRIDE: Read a single data trace from the data, in internal integer scaling.
    * Should return a defensive clone. Assumes that channel and range are within the data range!
    */
  def readTraceIntDVImpl(channel: Int, rangeFrValid: NNRangeValid /*range: Range.Inclusive, segment: Int*/): DV[Int] = {
    val res = DV.zeros[Int]( rangeFrValid.length )
    nounou.util.forJava(rangeFrValid.start, rangeFrValid.last + 1, rangeFrValid.step, (c: Int) => (res(c) = readPointIntImpl(channel, c, rangeFrValid.segment)))
    res
  }
  def readTraceDVImpl(channel: Int, rangeFrValid: NNRangeValid): DV[Double] = {
    val res = DV.zeros[Double]( rangeFrValid.length )
    nounou.util.forJava(rangeFrValid.start, rangeFrValid.last + 1, rangeFrValid.step, (c: Int) => (res(c) = readPointImpl(channel, c, rangeFrValid.segment)))
    res
  }

//  //<editor-fold defaultstate="collapsed" desc="reading a frame">
//
//  /** Read a single frame from the data, in internal integer scaling, for just the specified channels.
//    */
//  final def readFrame(frame: Int, channels: Array[Int], segment: Int): DV[Int] = {
//    loggerRequire(isRealisticFr(frame/*, segment*/), "Unrealistic frame/segment: " + (frame, segment).toString)
//    loggerRequire(channels.forall(isValidChannel), "Invalid channels: " + channels.toString)
//
//    if( isValidFr(frame/*, segment*/) ) readFrameImpl(frame, channels/*, segment*//*(currentSegment = segment)*/ ) else DV.zeros[Int]( channels.length )
//
//  }
//
//  /** Read a single frame from the data, in internal integer scaling.
//    */
//  final def readFrame(frame: Int, segment: Int): DV[Int] = {
//    loggerRequire(isRealisticFr(frame, segment), "Unrealistic frame/segment: " + (frame, segment).toString)
//    if( isValidFr(frame, segment) ) readFrameImpl(frame , segment ((currentSegment = segment)*/ ) else DV.zeros[Int]( channelCount )
//  }
////  final def readFrame(frame: Int): DV[Int] = readFrame(frame, 0)
//
//  final def readFrameA(frame: Int): Array[Int] = readFrame(frame).toArray
//  final def readFrameA(frame: Int, segment: Int): Array[Int] = readFrame(frame/*, segment*/).toArray
//  final def readFrameA(frame: Int, channels: Array[Int], segment: Int): Array[Int] = readFrame(frame, channels, segment).toArray
//
//  //</editor-fold>
//
//  /** CAN OVERRIDE: Read a single frame from the data, in internal integer scaling.
//    * Should return a defensive clone. Assumes that frame is within the data range!
//    */
//  def readFrameImpl(frame: Int): DV[Int] = {
//    val res = DV.zeros[Int](channelCount)
//    nounou.util.forJava(0, channelCount, 1, (channel: Int) => res(channel) = readPointImpl(channel, frame))
//    res
//  }
//  /** CAN OVERRIDE: Read a single frame from the data, for just the specified channels, in internal integer scaling.
//    * Should return a defensive clone. Assumes that frame and channels are within the data range!
//    */
//  def readFrameImpl(frame: Int, channels: Array[Int]): DV[Int] = {
//    val res = DV.zeros[Int]( channels.length )
//    nounou.util.forJava(0, channels.length, 1, (channel: Int) => res(channel) = readPointImpl(channel, frame))
//    res
//  }
//
//  // </editor-fold>

  // </editor-fold>

  override def isCompatible(that: NNElement): Boolean = {
    that match {
      case x: NNData => {
        ( //This is not enforced, may append objects with separate channel counts: channelCount() == x.channelCount()  &&
          timing().isCompatible(x.timing())) &&
          scale().isCompatible(x.scale())
        //&& this.layout.isCompatible(x.layout)
        //not channel info
      }
      case _ => false
    }
  }

//  override def :::(x: NNElement): NNData

}




///** Immutable version of XData.
//  *
//  * Must override the following:
//  * +  def readPointImpl(channel: Int, frame: Int, segment: Int): Int
//  * +  (from XFramesImmutable)
//  * ++   val segmentCount: Int
//  * ++   val length: : Vector[Int]
//  * ++   val segmentStartTSs: Vector[Long]
//  * ++   val sampleRate: Double
//  * +  (from XChannelsImmutable)
//  * ++   val channelNames: Vector[String]
//  * +  (from XAbsoluteImmutable)
//  * ++   val absGain: Double
//  * ++   val absOffset: Double
//  * ++   val absUnit: String
//  *
//  * Can override the following:
//  * +   def readTraceImpl(channel: Int, segment: Int): Vector[Int]
//  * +   def readTraceImpl(channel: Int, span:Span, segment: Int): Vector[Int]
//  * +   def readFrameImpl(frame: Int, segment: Int): Vector[Int]
//  * +   def readFrameImpl(frame: Int, channels: Vector[Int], segment: Int): Vector[Int]
//  * +  (from XAbsoluteImmutable)
//  * ++   val xBits: Int = 1024
//  */
//abstract class XDataImmutable extends XData with XFramesImmutable with XChannelsImmutable with XAbsoluteImmutable {
//
//
//  //ToDo 4: channelNames as lazy val?
//
//  // <editor-fold defaultstate="collapsed" desc=" DataSource related ">
//
//  override def changedData(): Unit = logger.error("this is an immutable data source, and changedData() should not be invoked!")
//  override def changedData(channel: Int): Unit = logger.error("this is an immutable data source, and changedData() should not be invoked!")
//  override def changedData(channels: Vector[Int]): Unit = logger.error("this is an immutable data source, and changedData() should not be invoked!")
//  override def changedTiming(): Unit = logger.error("this is an immutable data source, and changedTiming() should not be invoked!")
//
//  // </editor-fold>
//
//}

//
//class NNDataNull$ extends NNData {
//  override def readPointImpl(channel: Int, frame: Int, segment: Int): Int = 0
//  override val absGain: Double = 1D
//  override val absOffset: Double = 0D
//  override val absUnit: String = "Null unit"
//  override val scaleMax: Int = 0
//  override val scaleMin: Int = 0
//  override def segmentLengthImpl(segment: Int): Int = 0
//  override val segmentStartTs: Array[Long] = Array[Long]()
//  override val sampleRate: Double = 1d
////  override val layout: NNLayout = NNLayoutNull
//
//
//  override def :::(x: NNElement): NNData = x match {
//    case NNDataNull => this
//    case xData: NNData => xData
//    case xDataChannel: NNDataChannel => new NNDataChannelArray( Vector(xDataChannel) )
//    case _ => require(false, "cannot append incompatible data types (XDataNull)"); this
//  }
//  override def toString() = "XDataNull()"
//
//  override val segmentCount: Int = 0
//  override val channelCount: Int = 0
//
//  /** OVERRIDE: End timestamp for each segment. Implement by overriding _endTimestamp
//    */
//  override val segmentEndTs: Array[Long] = Array[Long]()
//}
//
//object NNDataNull extends NNDataNull$
//
//
//class NNDataAuxNull extends NNDataNull$ with NNDataAux {
//
//  override def :::(x: NNElement): NNDataAux = x match {
//    case xDataAux: NNDataAux => xDataAux
//    case _ => require(false, "cannot append incompatible data types (XDataAuxNull)"); this
//  }
//  override def toString() = "XDataAuxNull()"
//}
//
//object NNDataAuxNull extends NNDataAuxNull