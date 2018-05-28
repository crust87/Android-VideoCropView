# Android-VideoCropView
ViewView for crop video

## Update
### 1.1.1
add original ratio option

## Example

add build.gradle<br />
``` groovy
implementation 'com.crust87:video-crop-view:1.4.0'
```

append your layout xml
```xml
<android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <FrameLayout
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintDimensionRatio="1:1"
        android:layout_width="0dp"
        android:layout_height="0dp">

        <com.crust87.videocropview.VideoCropView
            android:id="@+id/videoCropView"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            app:ratio_height="4"
            app:ratio_width="3" />

    </FrameLayout>
</android.support.constraint.ConstraintLayout>
```

request video
```java
Intent lIntent = new Intent(Intent.ACTION_PICK);
lIntent.setType("video/*");
lIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
startActivityForResult(lIntent, 1000);
```

and set image Uri
```java
mVideoCropView.setVideoURI(selectedVideoUri);
```

![](./screenshot_01.png)

swipe video and change view matrix

![](./screenshot_02.png)

## License
Copyright 2015 Mabi

Licensed under the Apache License, Version 2.0 (the "License");<br/>
you may not use this work except in compliance with the License.<br/>
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software<br/>
distributed under the License is distributed on an "AS IS" BASIS,<br/>
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.<br/>
See the License for the specific language governing permissions and<br/>
limitations under the License.
