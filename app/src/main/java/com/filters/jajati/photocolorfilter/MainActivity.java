package com.filters.jajati.photocolorfilter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaActionSound;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener,CameraBridgeViewBase.CvCameraViewListener2{
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    double x = -1;
    double y = -1;
    private Scalar mBlobColorRgba,mBlobColorHsv;
    TextView touch_coordinates, touch_color;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        touch_color = (TextView)findViewById(R.id.touch_color);
        touch_coordinates = (TextView)findViewById(R.id.touch_coordinates);
        mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.opencv_filter_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause(){
        super.onPause();
        if (mOpenCvCameraView != null){mOpenCvCameraView.disableView();}
    }

    @Override
    public void onResume(){
        super.onResume();
        if(!OpenCVLoader.initDebug())
        {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0,this,mLoaderCallback);
        }
        else
        {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if(mOpenCvCameraView != null)
        {mOpenCvCameraView.disableView();}
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();
        double yLow = (double) mOpenCvCameraView .getHeight()*0.2401961;
        double yHigh = (double) mOpenCvCameraView .getHeight()*0.7696078;
        double xScale = (double) cols/(double)mOpenCvCameraView.getWidth();
        double yScale = (double) rows/(yHigh-yLow);
        x = event.getX();
        y = event.getY();
        y = y - yLow;
        x = x * xScale;
        y = y * yScale;
        if((x < 0)||(y < 0)||(x > cols)||(y > rows)) return false;
        touch_coordinates.setText("X : "+ Double.valueOf(x) +",   Y : "+Double.valueOf(y));
        Rect touchedRect=new Rect();
        touchedRect.x=(int)x;
        touchedRect.y=(int)y;
        touchedRect.width=8;
        touchedRect.height=8;
        Mat touchedRegionRgba=mRgba.submat(touchedRect);
        Mat touchedRegionHsv=new Mat();
        Imgproc.cvtColor(touchedRegionRgba,touchedRegionHsv,Imgproc.COLOR_RGB2HSV_FULL);
        Imgproc.cvtColor(touchedRegionRgba,touchedRegionHsv,Imgproc.COLOR_RGB2HSV_FULL);

        mBlobColorHsv= Core.sumElems(touchedRegionHsv);
        int pointCount=touchedRect.width*touchedRect.height;
        for(int i=0;i<mBlobColorHsv.val.length;i++)
            mBlobColorHsv.val[i]/=pointCount;

        mBlobColorRgba=convertScalarHsv2Rgba(mBlobColorHsv);

        touch_color.setText("Color : #"+ String.format("%02X", (int)mBlobColorRgba.val[0])
                +String.format("02X",(int)mBlobColorRgba.val[1])
                +String.format("02X",(int)mBlobColorRgba.val[2]));

        touch_color.setTextColor(Color.rgb((int)mBlobColorRgba.val[0],
                (int)mBlobColorRgba.val[1],(int)mBlobColorRgba.val[2]));
        touch_coordinates.setTextColor(Color.rgb((int)mBlobColorRgba.val[0],
                (int)mBlobColorRgba.val[1],(int)mBlobColorRgba.val[2]));

        MediaActionSound sound = new MediaActionSound();
        sound.play(MediaActionSound.SHUTTER_CLICK);

        // Save image
        Bitmap Image = Bitmap.createBitmap(mRgba.cols(), mRgba.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mRgba, Image);
        String greeting = "greeting";
        MediaStore.Images.Media.insertImage(getContentResolver(), Image, greeting , greeting);

        //Create filter
        Mat img = mRgba.clone();
        img.setTo(new Scalar((int)mBlobColorRgba.val[0],
                (int)mBlobColorRgba.val[1],
                (int)mBlobColorRgba.val[2]));


        // Overlay camera frame and filter
        Core.addWeighted(mRgba, 0.3, img, 0.7, 0, img);

        // Sharpen
        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2YCrCb);

        List<Mat> channels = new ArrayList<Mat>(3);
        Core.split(img,channels);

        Imgproc.equalizeHist(channels.get(0), channels.get(0));

        Core.merge(channels,img);
        Imgproc.cvtColor(img,img,Imgproc.COLOR_YCrCb2RGB);

        // Toast
        Context context = getApplicationContext();
        CharSequence text = "Saving filtered image with\nColor #" +
                String.format("%02X", (int)mBlobColorRgba.val[0])
                +String.format("02X",(int)mBlobColorRgba.val[1])
                +String.format("02X",(int)mBlobColorRgba.val[2]);
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();


        // Save filter image to gallery
        Bitmap Imag = Bitmap.createBitmap(img.cols(), img.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, Imag);
        String greet = "greet";
        MediaStore.Images.Media.insertImage(getContentResolver(), Imag, greet , greet);

        return false;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba=new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv=new Scalar(255);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba=inputFrame.rgba();
        return mRgba;
    }

    private Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba=new Mat();
        Mat pointMatHsv=new Mat(1,1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv,pointMatRgba,Imgproc.COLOR_HLS2RGB_FULL,4);
        return new  Scalar(pointMatRgba.get(0,0));
    }

}