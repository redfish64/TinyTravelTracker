<map version="freeplane 1.7.0">
<!--To view this file, download free mind mapping software Freeplane from http://freeplane.sourceforge.net -->
<node TEXT="OTT" FOLDED="false" ID="ID_1540936311" CREATED="1556681619487" MODIFIED="1651415117022"><hook NAME="MapStyle">
    <properties edgeColorConfiguration="#808080ff,#ff0000ff,#0000ffff,#00ff00ff,#ff00ffff,#00ffffff,#7c0000ff,#00007cff,#007c00ff,#7c007cff,#007c7cff,#7c7c00ff" fit_to_viewport="false"/>

<map_styles>
<stylenode LOCALIZED_TEXT="styles.root_node" STYLE="oval" UNIFORM_SHAPE="true" VGAP_QUANTITY="24.0 pt">
<font SIZE="24"/>
<stylenode LOCALIZED_TEXT="styles.predefined" POSITION="right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="default" ICON_SIZE="12.0 pt" COLOR="#000000" STYLE="fork">
<font NAME="SansSerif" SIZE="10" BOLD="false" ITALIC="false"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.details"/>
<stylenode LOCALIZED_TEXT="defaultstyle.attributes">
<font SIZE="9"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.note" COLOR="#000000" BACKGROUND_COLOR="#ffffff" TEXT_ALIGN="LEFT"/>
<stylenode LOCALIZED_TEXT="defaultstyle.floating">
<edge STYLE="hide_edge"/>
<cloud COLOR="#f0f0f0" SHAPE="ROUND_RECT"/>
</stylenode>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.user-defined" POSITION="right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="styles.topic" COLOR="#18898b" STYLE="fork">
<font NAME="Liberation Sans" SIZE="10" BOLD="true"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.subtopic" COLOR="#cc3300" STYLE="fork">
<font NAME="Liberation Sans" SIZE="10" BOLD="true"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.subsubtopic" COLOR="#669900">
<font NAME="Liberation Sans" SIZE="10" BOLD="true"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.important">
<icon BUILTIN="yes"/>
</stylenode>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.AutomaticLayout" POSITION="right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="AutomaticLayout.level.root" COLOR="#000000" STYLE="oval" SHAPE_HORIZONTAL_MARGIN="10.0 pt" SHAPE_VERTICAL_MARGIN="10.0 pt">
<font SIZE="18"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,1" COLOR="#0033ff">
<font SIZE="16"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,2" COLOR="#00b439">
<font SIZE="14"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,3" COLOR="#990000">
<font SIZE="12"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,4" COLOR="#111111">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,5"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,6"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,7"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,8"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,9"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,10"/>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,11"/>
</stylenode>
</stylenode>
</map_styles>
</hook>
<node TEXT="2019/5/2" FOLDED="true" POSITION="right" ID="ID_74087139" CREATED="1556681657696" MODIFIED="1556760199556">
<node TEXT="todo" FOLDED="true" ID="ID_254337593" CREATED="1556760200628" MODIFIED="1556760201312">
<node TEXT="get rid of onsceneready hack" FOLDED="true" ID="ID_1405293391" CREATED="1556760202154" MODIFIED="1556760208630">
<node TEXT="done" ID="ID_1089431796" CREATED="1556760783605" MODIFIED="1556760784187"/>
</node>
<node TEXT="fix timeline movement not working" FOLDED="true" ID="ID_978724935" CREATED="1556760208954" MODIFIED="1556760216749">
<node TEXT="done" ID="ID_390793880" CREATED="1556760780828" MODIFIED="1556760781672"/>
</node>
<node TEXT="long press select points" FOLDED="true" ID="ID_1105625448" CREATED="1556760217060" MODIFIED="1556760231868">
<node TEXT="the problem is that longpress normally doesn&apos;t work with pan" FOLDED="true" ID="ID_503221133" CREATED="1556764391988" MODIFIED="1556764402272">
<node TEXT="pan won&apos;t activate after a long press has been detected" ID="ID_943432033" CREATED="1556764403190" MODIFIED="1556764413110"/>
</node>
<node TEXT="mytouchinput will create its own gesturelistener just for long press" ID="ID_946320077" CREATED="1556764046206" MODIFIED="1556764389590"/>
</node>
<node TEXT="search for fixmes" ID="ID_746794742" CREATED="1556760238747" MODIFIED="1556760240980"/>
</node>
</node>
<node TEXT="2019/5/6" FOLDED="true" POSITION="right" ID="ID_1808752345" CREATED="1557104818549" MODIFIED="1557104830017">
<node TEXT="font scaling" FOLDED="true" ID="ID_106721565" CREATED="1557104831310" MODIFIED="1557104841546">
<node TEXT="create custom map" FOLDED="true" ID="ID_1336549839" CREATED="1557104845458" MODIFIED="1557104849181">
<node TEXT="hard code font scaling and double it or whatever" ID="ID_1306576423" CREATED="1557104853069" MODIFIED="1557104860507"/>
<node TEXT="create functions for all fonts" ID="ID_794611790" CREATED="1557104862242" MODIFIED="1557104868915"/>
</node>
<node TEXT="multiply opengl matrix" FOLDED="true" ID="ID_669032642" CREATED="1557104876749" MODIFIED="1557104884939">
<node TEXT="This would make all maps automatically scaled" ID="ID_1482547786" CREATED="1557104886613" MODIFIED="1557104897356"/>
<node TEXT="We could piggyback this to render our points directly on the gl surface rather than use the crappy ui way" ID="ID_1917924276" CREATED="1557106153834" MODIFIED="1557106170988"/>
<node TEXT="panning would be affected" ID="ID_1570017006" CREATED="1557104944332" MODIFIED="1557104958562"/>
<node TEXT="In the map view" FOLDED="true" ID="ID_361732594" CREATED="1557105474469" MODIFIED="1557105990962">
<node TEXT="GlViewHolderFactory" ID="ID_1402187713" CREATED="1557105482559" MODIFIED="1557105500952"/>
<node TEXT="GLViewHolder" ID="ID_810165955" CREATED="1557105518308" MODIFIED="1557105522238"/>
<node TEXT="GLSurfaceView" FOLDED="true" ID="ID_511805009" CREATED="1557105990957" MODIFIED="1557105993648">
<node TEXT="setGLWrapper" FOLDED="true" ID="ID_1628774574" CREATED="1557105865254" MODIFIED="1557105869374">
<node TEXT="wraps a GL interface in another GL interface" ID="ID_1942626161" CREATED="1557105917833" MODIFIED="1557105926147"/>
</node>
<node TEXT="setRenderer" FOLDED="true" ID="ID_256740864" CREATED="1557105994342" MODIFIED="1557105999762">
<node TEXT="must only be called once" ID="ID_1574469694" CREATED="1557106001126" MODIFIED="1557106003922"/>
</node>
</node>
</node>
<node TEXT="replace renderer" FOLDED="true" ID="ID_14619739" CREATED="1557106053144" MODIFIED="1557106055887">
<node TEXT="override onMapInitUIThread" FOLDED="true" ID="ID_1944958580" CREATED="1557106136376" MODIFIED="1557106150792">
<node TEXT="in MapView" ID="ID_1221869715" CREATED="1557106213427" MODIFIED="1557106216794"/>
<node TEXT="we can pass our own GLViewHolderFactory" FOLDED="true" ID="ID_1949084958" CREATED="1557106217230" MODIFIED="1557106249559">
<node TEXT="The implementation of this is GLSurfaceViewHolderFactory" ID="ID_1556632149" CREATED="1557106436091" MODIFIED="1557106448661"/>
</node>
<node TEXT="MapController calls GlSurfaceViewHolder.setRenderer()" FOLDED="true" ID="ID_1076879531" CREATED="1557106356722" MODIFIED="1557106540512">
<node TEXT="In UIThreadInit" FOLDED="true" ID="ID_1129331985" CREATED="1557106577982" MODIFIED="1557106587943">
<node TEXT="package viewable method" ID="ID_496005786" CREATED="1557106588745" MODIFIED="1557106592605"/>
<node TEXT="Called by MapView" FOLDED="true" ID="ID_95928535" CREATED="1557106596101" MODIFIED="1557106599587">
<node TEXT="In onMapInitOnUIThread" FOLDED="true" ID="ID_865036701" CREATED="1557106658790" MODIFIED="1557106660762">
<node TEXT="protected" ID="ID_467668961" CREATED="1557106663318" MODIFIED="1557106665030"/>
</node>
</node>
</node>
</node>
</node>
</node>
<node TEXT="Override onDrawFrame in MapController" FOLDED="true" ID="ID_1144243620" CREATED="1557106737367" MODIFIED="1557106751233">
<node TEXT="we already have MyMapController" ID="ID_549147672" CREATED="1557106751843" MODIFIED="1557106758115"/>
<node TEXT="We just call glMultMatrix?" ID="ID_1594787119" CREATED="1557106758403" MODIFIED="1557106800567"/>
<node TEXT="glScale" ID="ID_1272630953" CREATED="1557106951587" MODIFIED="1557106953771"/>
<node TEXT="doesn&apos;t work, nativeRender or nativeUpdate must be resetting it" ID="ID_1431270730" CREATED="1557107489319" MODIFIED="1557107508520"/>
</node>
<node TEXT="update pixel scale?" FOLDED="true" ID="ID_155437269" CREATED="1557107885859" MODIFIED="1557107893561">
<node TEXT="inside mapcontroller" FOLDED="true" ID="ID_1524058659" CREATED="1557107896399" MODIFIED="1557107901083">
<node TEXT="onSurfaceChanged" ID="ID_59211368" CREATED="1557107902895" MODIFIED="1557107905551"/>
<node TEXT="can&apos;t call native methods" FOLDED="true" ID="ID_1612249459" CREATED="1557107986194" MODIFIED="1557107992130">
<node TEXT="this is the only way to call" ID="ID_618023000" CREATED="1557107993474" MODIFIED="1557107996644"/>
</node>
</node>
</node>
<node TEXT="alter displaymetrics?" FOLDED="true" ID="ID_405027497" CREATED="1557108210848" MODIFIED="1557108215445">
<node TEXT="this is a private field in mapcontroller" ID="ID_103749665" CREATED="1557108217559" MODIFIED="1557108222451"/>
<node TEXT="populated with" FOLDED="true" ID="ID_1271344471" CREATED="1557108223231" MODIFIED="1557108229191">
<node TEXT="        displayMetrics = context.getResources().getDisplayMetrics(); " ID="ID_1586903230" CREATED="1557108245995" MODIFIED="1557108247487"/>
</node>
<node TEXT="we can modify this, but it&apos;s very hacky" FOLDED="true" ID="ID_284504717" CREATED="1557108417023" MODIFIED="1557108423128">
<node TEXT="we&apos;d be updating for the entire app, not so nice" ID="ID_1463488891" CREATED="1557108425949" MODIFIED="1557108440254"/>
</node>
</node>
<node TEXT="create our own jni c class that mucks with the map" FOLDED="true" ID="ID_1005505005" CREATED="1557108790361" MODIFIED="1557108802284">
<node TEXT="we need mapPointer" ID="ID_970933001" CREATED="1557108805850" MODIFIED="1557108811220"/>
</node>
</node>
<node TEXT="camera manipulations?" ID="ID_493419586" CREATED="1557104905934" MODIFIED="1557104917946"/>
<node TEXT="zoom level muckery?" ID="ID_632812551" CREATED="1557104923768" MODIFIED="1557104930792"/>
</node>
</node>
</node>
</map>
