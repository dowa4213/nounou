package nounou.elements.data.filters

import breeze.linalg.DenseVector
import nounou.NN
import nounou.io.neuralynx.NNTestLoaderNCS_Tet4
import org.scalatest.FunSuite


/**
 * @author ktakagaki
 * //@date 2/19/14.
 */
class NNFilterDecimateTest extends FunSuite with NNTestLoaderNCS_Tet4 {

  val filtObj = new NNFilterDecimate( dataObjTet4, 5 )


  test("timing info"){

    //println(filtObj.timing.toStringFull())
    assert( filtObj.timing.filterDelay == 0)
    assert( filtObj.timing.segmentCount == 3 )
    assert( filtObj.timing.segmentLength(0) == 667853 )
    assert( filtObj.timing.segmentLength(1) == 24679 )
    assert( filtObj.timing.segmentLength(2) == 1350964 )

    assert( filtObj.timing.segmentStartFrame(1) == 667853 )

    assert( filtObj.timing.segmentStartTss(0) == 2592255824L )
    assert( filtObj.timing.segmentStartTss(1) == 4596169824L )
    assert( filtObj.timing.segmentStartTss(2) == 4627405824L )

  }

  test("readTrace"){

    assert( dataObjTet4.readTraceDV(0, NN.NNRange(0,10,1,0)) ==
      DenseVector(-27.100428000000004, -18.4026555, -3.2654795000000005, 0.030518500000000004, -3.1434055000000005,
                  -3.1128870000000006, -4.486219500000001, -11.169771, -16.937767500000003, -15.869620000000001,
                  -7.751699000000001) )
    assert( filtObj.readTraceDV(0, NN.NNRange(0,2,1,0)) ==
      DenseVector(-8.63752852893744, -6.528832765988315, -9.400263418622112)
    )
    assert( filtObj.getFactor() == 5 )

//    assert(filtObj.readTraceDV( 0, NN.NNRange(0, 10, 1, segment = 0)) ==
//      DenseVector(-27.100428000000004, -3.1128870000000006, -7.751699000000001, -21.210357500000004, -7.019255000000001, -30.457463000000004, -14.770954000000001,
//        -19.470803000000004, -21.668135000000003, -3.5706645000000004, -12.390511000000002)
//    )
//    assert(filtObj.readTraceDV( 2, NN.NNRange(11, 14, 1, segment = 2)) ==
//      DenseVector(-116.36704050000002, -107.85237900000001, -121.34155600000001, -137.7299905)
//    )

  }

}
