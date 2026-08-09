[Skip to main content](https://m3.material.io/blog/m3-expressive-motion-theming#main_content)[search](https://m3.material.io/search.html) [material\_design\\
Home](https://m3.material.io/) [apps\\
Get started](https://m3.material.io/get-started) [code\\
Develop](https://m3.material.io/develop) [book\\
Foundations](https://m3.material.io/foundations) [palette\\
Styles](https://m3.material.io/styles) [add\_circle\\
Components](https://m3.material.io/components) [pages\\
Blog](https://m3.material.io/blog)

play\_arrow

pause

dark\_mode

light\_mode

May 20, 2025

# Adding Motion Physics with Jetpack Compose

Supercharge your Android transitions and animations with the new M3 Expressive motion theming system.

On this page

## Adding Motion Physics with Jetpack Compose

- Why Material motion?
- Getting setup
- Fundamentals of the motion system
- Animation specs: Spatial or Effect
- Why use motion springs?
- Customizing your motion scheme
- Custom component animations
- Individual Material Component changes
- Get started today
- Up next

Share on

![share on X](https://m3.material.io/static/assets/x.svg)

![share on Facebook](https://m3.material.io/static/assets/facebook.svg)

![share on Linkedin](https://m3.material.io/static/assets/linkdin.svg)

link

Copy linkLink copied

Posted by

**Rebecca Franks**, Developer Relations Engineer, Android

**Austin Fisher**, Sr. UX Content Designer

**Gus Sonoda**, Sr. Staff Motion Designer

* * *

link

Copy linkLink copied

Material Design has an exciting new preview release — [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive?utm_source=blog&utm_medium=referral&utm_campaign=IO25). In this latest version 1.4.0-alpha14 of Material 3, you get access to a new theming system: motion!

Previously, Material motion was defined through non-customizable easing and duration values. Today, we’re introducing a new, customizable motion scheme using motion physics defined through a set of motion properties. These can be customized or overridden as needed, giving you more control than ever over how motion works and feels in your product.

Read on to learn how the new motion physics scheme works, how existing APIs have changed, and how to get started.

link

Copy linkLink copied

pause

link

Copy linkLink copied

## Why Material motion?

As a product developer, you know that motion can significantly enhance the user experience. But achieving consistent motion across a complex app can be challenging. The new Material motion system solves this with:

- **Centralized control:** Define your motion theme once, and all Material Components and even your own custom components will inherit it, creating a unified and polished experience, eliminating scattered animation specs.

- **Simplified theming:** Stop fussing with individual duration or easing values. Choose from predefined schemes or create your own, using physics-based spring animations for a natural and engaging feel.

- **Adaptive animations:** Ensure that movement feels fast in the context of the device and adjusts based on user input since animations are not based on predefined time sets.


link

Copy linkLink copied

![](https://firebasestorage.googleapis.com/v0/b/design-spec/o/projects%2Fm3%2Fimages%2Fmn4fyuj5-test.gif?alt=media&token=30384b36-9ce9-4923-9ae7-eb1d868d5ca5)

link

Copy linkLink copied

## Getting setup

First, make sure you’re using the latest version of Compose Material 3:

link

Copy linkLink copied

```
androidx.compose.material3:material3:1.4.0-alpha11
```

link

Copy linkLink copied

If you do nothing more than update the Compose dependency, your apps will benefit from applying the standard motion scheme to all your uses of Material Components. But if you do want that extra bit of control, you can customize your scheme to suit your app.

_Note: When 1.4.0 goes to stable, the Material Expressive APIs will move to the next alpha (1.5.0-alphaX), and will no longer be available in 1.4.0. The APIs will go stable in the 1.5.0 release._

link

Copy linkLink copied

## Fundamentals of the motion system

link

Copy linkLink copied

### Motion schemes: Expressive or Standard

The physics system has two preset motion schemes: **Expressive** and **Standard**. The scheme you choose will define how your product feels. While most motion in a product should use the same scheme, advanced customizations allow you to swap the scheme to emphasize key moments.

- **Expressive** is Material’s recommended motion scheme, and should be used for most situations, particularly hero moments and key interactions.

- **Standard,** with its small amount of bounce, feels more functional and should be used for utilitarian products.


link

Copy linkLink copied

pause

Expressive: The Expressive motion scheme overshoots the final values to add bounce.

pause

Standard: The Standard motion scheme eases into the final values.

link

Copy linkLink copied

## Animation specs: Spatial or Effect

Two distinct kinds of specifications make up the motion scheme: **Spatial** animation specs and **Effect** animation specs.

link

Copy linkLink copied

**Spatial** specs are used to animate changes in an object's position, orientation, size, and shape. The spring overshoots the final value and bounces into place.

![](https://lh3.googleusercontent.com/CWMlcn4iysCXeyvgc4NYGXLGuYc3XxUX4r0sZxJb0R1YDks9HSC2hQptFExqmD7wCboG270-b7pgZ3zaP-fN8RusrH9iq3Dhh0Pj7_oclOlB=w40)

Spatial springs applied to movement.

![](https://lh3.googleusercontent.com/jQ-CxwzqRQJnmGKbSTD0CDhFD2NQTRKjd4bXZnKoEz1v-_BFuKvYlqacoBt4z3OUF_9WQpOaxcXONhlNWK7lnmE4_I6GA8p-ybdiddHyODpq=w40)

Spatial springs applied to rotation.

link

Copy linkLink copied

**Effect** specs are used to animate an object’s properties such as color and opacity, where there shouldn’t be any overshoot.

![](https://lh3.googleusercontent.com/EFgd_M-QGccSaDTuqe64lZDDdXZwZPylVH5nqsy1vnlI7U_sxvOycf8oEkeC7sm77p7E3Ok__1u5TCSapKdPvtLlpv8GIZaHa2ewZW52S0hT=w40)

Effects springs applied to opacity.

![](https://lh3.googleusercontent.com/Bh3-_cViFNE23c15P8Ts_Nvtr0KKHUsfcSEOywGLhjSc3lAdN9xN7DygUt41oijdV3V5zkaw0FbACi3V6yMB3895X50UjWdG_CCkKbBKBh7MgQ=w40)

Effects springs applied to color.

link

Copy linkLink copied

Each animation can also have one of three speeds: **default**, **fast**, and **slow**. Most motion should use the default speed, but smaller elements may benefit from the fast speed and larger elements from the slow.

link

Copy linkLink copied

|     |     |     |
| --- | --- | --- |
| Speed | Spatial example | Effects example |
| Default | Animations that partially cover the screen, such as [bottom sheets](https://m3.material.io/m3/pages/bottom-sheets/overview) or [expanded navigation rails](https://m3.material.io/m3/pages/navigation-rail/overview) | Opacity of the content within a navigation rail |
| Fast | Animations for small components such as [switches](https://m3.material.io/m3/pages/switch/overview) and [buttons](https://m3.material.io/m3/pages/common-buttons/overview) | Color change of the switch handle |
| Slow | Full-screen [animations](https://m3.material.io/m3/pages/motion-transitions/transition-patterns/) | Full-screen content refresh |

link

Copy linkLink copied

**Speed tokens work across devices.** For example, the **Spatial “fast”** token will always be faster than **“default”** or **“slow,”** but the exact values of each token will differ depending on whether the device is a wearable, phone, or tablet. This ensures the movement feels fast in the context of the device. This also applies when using spring tokens in a custom motion scheme.

link

Copy linkLink copied

pause

Effects motion in fast, default, and slow speeds

pause

Spatial motion in fast, default, and slow speeds

link

Copy linkLink copied

Notice that the Expressive and Standard schemes are presets of opinionated motion values. This makes it easier to swap schemes without changing the underlying property names.

link

Copy linkLink copied

![](https://lh3.googleusercontent.com/smuf6EuWMbN77Hv3d_SunEmOcTz0ZeZWerftjSIgz2Aw55bwpI8yR5DSuq6YEqV5WrJVzWOFQPzmCLLexaDJYewcpnb0O4eu4OBanGqVo3XLFg=w40)

Expressive and Standard MotionScheme

link

Copy linkLink copied

## Why use motion springs?

If you look at the predefined specifications on the motion schemes, you may notice that springs are used for the backing specifications. Why is this? Spring animations appear more natural by allowing for the ability to interrupt and retarget the animation when required.

For example, consider the difference between using tween and springs:

link

Copy linkLink copied

pause

Tween interruptions.

pause

Spring interruptions.

link

Copy linkLink copied

When interrupted and retargeted to a new destination, the spring animation uses its current velocity to perform a more seamless transition between the two states than tween.

It’s also beneficial to use springs to ensure your animations easily **adapt to different screen sizes**, as the tokens will always be slow or fast in the context of the device since it is not time that is specified but rather damping and stiffness. For more details on the benefits of springs, check out the [documentation](https://developer.android.com/develop/ui/compose/animation/customize?_gl=1*9ewdxh*_ga*MzI1NjMyOTExLjE3NjI0MTE3NDU.*_ga_QPQ2NRV856*czE3NzQzNDgyNDgkbzE3OCRnMCR0MTc3NDM0ODM1MCRqNjAkbDAkaDA.#spring?utm_source=blog&utm_medium=motion&utm_campaign=IO25).

link

Copy linkLink copied

## Customizing your motion scheme

The real power of the new system lies in its flexibility. You can tailor the motion scheme to reflect your app’s brand or to underscore an important interaction.

link

Copy linkLink copied

### Choosing between Standard and Expressive schemes

Most developers won’t want to change much in terms of motion theming, but there are two out-of-the-box options for you to choose from: Standard or Expressive. Choosing MaterialExpressiveTheme at the top level will default to the Expressive motion scheme.

Make this choice once for your application at the theme level, and any Material Components with movement built in will use the selected spec from the theme.

link

Copy linkLink copied

​x

```
@Composable
```

```
fun YourCustomTheme(){
```

```
   MaterialExpressiveTheme(
```

```
      motionScheme = MotionScheme.expressive(), // or MotionScheme.standard()
```

```
   ) {
```

```
​
```

```
   }
```

```
}
```

link

Copy linkLink copied

With the expressive() scheme your apps take on the Material-recommended motion scheme, whereas with the standard(), the motion is, well, pretty standard — there is a minimal amount of bounce, even as both use springs under the hood.

link

Copy linkLink copied

### Creating your own Motion scheme

For more fine-grained control, you can create your own MotionScheme object and return a different AnimationSpec for each property.

In the code snippet below, we create a whole new playfulMotionScheme that by default adds a lot of bounce to components. This demonstrates how you can customize your MotionScheme.

link

Copy linkLink copied

```
xxxxxxxxxx
```

```
@ExperimentalMaterial3ExpressiveApi
```

```
fun playfulMotionScheme(): MotionScheme =
```

```
    object : MotionScheme {
```

```
​
```

```
        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> {
```

```
            return spring(
```

```
                dampingRatio = Spring.DampingRatioNoBouncy,
```

```
                stiffness = 1600f
```

```
            )
```

```
        }
```

```
​
```

```
        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> {
```

```
            return spring(dampingRatio = 0.6f, stiffness = 700f)
```

```
        }
```

```
​
```

```
        override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> {
```

```
            return spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)
```

```
        }
```

```
​
```

```
        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> {
```

```
            return spring(dampingRatio = 0.6f, stiffness = 1400f)
```

```
        }
```

```
​
```

```
        override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> {
```

```
            return spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
```

```
        }
```

```
​
```

```
        override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> {
```

```
            return spring(dampingRatio = 0.6f, stiffness = 300f)
```

```
        }
```

```
    }
```

```
​
```

```
​
```

```
// To use the scheme: In your top level composable
```

```
​
```

```
@Composable
```

```
fun MyCustomTheme(
```

```
    //... other theme properties
```

```
    content: @Composable () -> Unit
```

```
) {
```

```
    MaterialExpressiveTheme(
```

```
        //.... other theme properties
```

```
        motionScheme = playfulMotionScheme(),
```

```
        content = {
```

```
            content()
```

```
        }
```

```
    )
```

```
}
```

link

Copy linkLink copied

pause

FAB menu with an extra-stiff custom scheme.

pause

FAB menu with very low stiffness custom scheme.

link

Copy linkLink copied

## Custom component animations

To maintain consistency with other Material Components, ensure your custom components adopt the recommended motion changes and use the motion specs available through MaterialTheme.motionScheme.

For example, the following code uses tween() calls to animate the size and color of a text component when pressing and releasing its container:

link

Copy linkLink copied

```
xxxxxxxxxx
```

```
val interactionSource = remember { MutableInteractionSource() }
```

```
val isPressed by interactionSource.collectIsPressedAsState()
```

```
val scale by
```

```
  animateFloatAsState(
```

```
      targetValue = if (isPressed) 8f else 1f,
```

```
      animationSpec = tween(1000),
```

```
      label = "scale"
```

```
  )
```

```
val color by
```

```
  animateColorAsState(
```

```
      targetValue = if (isPressed) Color.Green else Color.Red,
```

```
      animationSpec = tween(1000),
```

```
      label = "color"
```

```
  )
```

```
Box(
```

```
  modifier =
```

```
      Modifier.fillMaxSize().clickable(
```

```
          interactionSource = interactionSource,
```

```
          indication = null
```

```
      ) {}
```

```
) {
```

```
  Text(
```

```
      text = "Hello",
```

```
      modifier =
```

```
          Modifier.graphicsLayer {
```

```
                  scaleX = scale
```

```
                  scaleY = scale
```

```
                  transformOrigin = TransformOrigin.Center
```

```
              }
```

```
              .align(Alignment.Center),
```

```
      style = LocalTextStyle.current.copy(color = color, textMotion = TextMotion.Animated)
```

```
  )
```

```
}
```

link

Copy linkLink copied

Since scale represents a Spatial motion and color falls under Effects motion, we can leverage the motion scheme for consistent application. This approach aligns your component’s motion to the overall app experience and allows it to adapt automatically when switching between Standard and Expressive schemes/themes.

link

Copy linkLink copied

```
xxxxxxxxxx
```

```
val interactionSource = remember { MutableInteractionSource() }
```

```
val isPressed by interactionSource.collectIsPressedAsState()
```

```
val scale by
```

```
  animateFloatAsState(
```

```
      targetValue = if (isPressed) 8f else 1f,
```

```
      animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
```

```
      label = "scale"
```

```
  )
```

```
val color by
```

```
  animateColorAsState(
```

```
      targetValue = if (isPressed) Color.Green else Color.Red,
```

```
      animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>(),
```

```
      label = "color"
```

```
  )
```

```
Box(
```

```
  modifier =
```

```
      Modifier.fillMaxSize().clickable(
```

```
          interactionSource = interactionSource,
```

```
          indication = null
```

```
      ) {}
```

```
) {
```

```
  Text(
```

```
      text = "Hello",
```

```
      modifier =
```

```
          Modifier.graphicsLayer {
```

```
                  scaleX = scale
```

```
                  scaleY = scale
```

```
                  transformOrigin = TransformOrigin.Center
```

```
              }
```

```
              .align(Alignment.Center),
```

```
      style = LocalTextStyle.current.copy(color = color, textMotion = TextMotion.Animated)
```

```
  )
```

```
}
```

link

Copy linkLink copied

## Individual Material Component changes

In the expressive update, most Material 3 components use the motion physics system by default.

To add the motion-physics system to other components, including those that are custom built, use the specifications from above. See the full [M3 Expressive announcement blog post](https://m3.material.io/blog/building-with-m3-expressive?utm_source=blog&utm_medium=motion&utm_campaign=IO25) for more details on the new components.

link

Copy linkLink copied

## Get started today

Try out the new motion subsystem in Material 3 Expressive today, and let us know how you like it by tagging us on social media @GoogleDesign. If you find any issues, please file them on the [issue tracker](https://b.corp.google.com/issues/new?component=742043&template=1590761?utm_source=blog&utm_medium=referral&utm_campaign=IO25).

Happy theming!

_Special thanks to Material motion designers Gus Winkelman and Gustavo Gonzalez._

link

Copy linkLink copied

## Up next

link

Copy linkLink copied

[Start building with M3 Expressive\\
\\
![](https://firebasestorage.googleapis.com/v0/b/design-spec/o/projects%2Fm3%2Fimages%2Fmn4mdykz-unnamed6.png?alt=media&token=8ac04d8c-31ab-4897-8fd3-cd6e9755e475=w960)](https://m3.material.io/blog/building-with-m3-expressive)

vertical\_align\_top

[material\_design](https://m3.material.io/)

Material Design is an adaptable system of guidelines, components, and tools that support the best practices of user interface design. Backed by open-source code, Material Design streamlines collaboration between designers and developers, and helps teams quickly build beautiful products.

- ### Social

- [GitHub](https://www.github.com/material-components)
- [X](https://x.com/googledesign)
- [YouTube](https://www.youtube.com/@googledesign)
- [Blog RSS](https://material.io/feed.xml)

- ### Libraries

- [Android](https://m3.material.io/develop/android/mdc-android)
- [Compose](https://m3.material.io/develop/android/jetpack-compose)
- [Flutter](https://m3.material.io/develop/flutter)
- [Web](https://m3.material.io/develop/web)

- ### More Google sites

- [Google Design](https://design.google/?home=)

- ### Archived versions

- [Material Design 1](https://m1.material.io/)
- [Material Design 2](https://m2.material.io/)

[Google](https://www.google.com/)

- [Privacy Policy](https://policies.google.com/privacy)
- [Terms of Service](https://policies.google.com/terms)
- [Join research studies](https://google.qualtrics.com/jfe/form/SV_3NMIMtX0F2zkakR?utm_source=Website&Q_Language=en&utm_campaign=Q2&campaignDate=June2022&referral_code=UXRgbtM2422655&productTag=b2d)
- Feedback